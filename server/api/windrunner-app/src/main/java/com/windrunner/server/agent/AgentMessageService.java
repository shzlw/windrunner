package com.windrunner.server.agent;

import com.windrunner.server.agent.api.AgentMessageResponse;
import com.windrunner.server.agent.domain.AgentMessageRoute;
import com.windrunner.server.agent.persistence.AgentMessageRequestRepository;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.chat.ChatService;
import com.windrunner.server.chat.api.ChatSessionContextView;
import com.windrunner.server.chat.api.ChatSessionView;
import com.windrunner.server.chat.domain.ChatMessage;
import com.windrunner.server.chat.domain.ChatSession;
import com.windrunner.server.chat.domain.ChatSessionContext;
import com.windrunner.server.chat.persistence.ChatMessageRepository;
import com.windrunner.server.chat.persistence.ChatSessionRepository;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.llm.LlmException;
import com.windrunner.server.llm.LlmMessage;
import com.windrunner.server.llm.LlmResult;
import com.windrunner.server.llm.LlmService;
import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.llm.LlmUsageContext;
import com.windrunner.server.llm.LlmUsageService;
import com.windrunner.server.llm.domain.LlmUsageFeature;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.tools.ToolRegistry;
import com.windrunner.server.tools.chat.FindProjectsTool;
import com.windrunner.server.tools.chat.ProposeWorkspaceChangesTool;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.WorkspaceChangeProposalService;
import com.windrunner.server.work.api.WorkspaceChangeProposalView;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class AgentMessageService {
    private static final int MAX_MESSAGE_LENGTH = 20_000;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;
    private static final int MAX_CANDIDATE_CHAT_SESSIONS = 8;
    private static final int MAX_CANDIDATE_MESSAGES = 4;
    private static final int MAX_HISTORY_MESSAGES = 50;
    private static final int MAX_HISTORY_LENGTH = 100_000;
    private static final int MAX_CONTEXT_PROJECTS = 10;
    private static final int MAX_MENTIONED_PROJECTS = 5;

    private final ObjectProvider<LlmService> llmServiceProvider;
    private final AgentMessageRequestRepository agentMessageRequestRepository;
    private final ChatService chatService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final WorkItemRepository workItemRepository;
    private final ToolRegistry toolRegistry;
    private final FindProjectsTool findProjectsTool;
    private final ProposeWorkspaceChangesTool proposeWorkspaceChangesTool;
    private final WorkspaceChangeProposalService workspaceChangeProposalService;
    private final LlmUsageService llmUsageService;
    private final EntityIdGenerator idGenerator;
    private final AppUserRepository appUserRepository;
    private final TransactionTemplate transactions;

    public AgentMessageResponse process(AppUser actor, String requestedIdempotencyKey, String requestedMessage) {
        Objects.requireNonNull(actor, "actor is required");
        String message = requireMessage(requestedMessage);
        String key = requireIdempotencyKey(requestedIdempotencyKey);
        AgentMessageRoute request = transactions.execute(status -> {
            agentMessageRequestRepository.lockIntake(actor.getId());
            agentMessageRequestRepository.insert(idGenerator.generate(EntityIdType.AGENT_MESSAGE_REQUEST),
                    actor.getId(), key, message);
            AgentMessageRoute stored = agentMessageRequestRepository.findByKey(actor.getId(), key)
                    .orElseThrow(() -> new IllegalStateException("Agent message could not be loaded"));
            if (!stored.getMessage().equals(message)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different message");
            }
            return stored;
        });
        return response(request, actor);
    }

    public AgentMessageResponse get(String requestId, AppUser actor) {
        return response(agentMessageRequestRepository.findForUser(requestId, actor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Agent message request not found")), actor);
    }

    private AgentMessageResponse response(AgentMessageRoute request, AppUser actor) {
        AgentMessageResponse.Routing routing = null;
        String reply = null;
        List<WorkspaceChangeProposalView> proposals = List.of();
        if (request.getRoutedChatSessionId() != null) {
            ChatSession session = chatService.getSessionForChat(request.getRoutedChatSessionId(), actor.getId());
            routing = new AgentMessageResponse.Routing(request.getRoutingDecision(),
                    new AgentMessageResponse.ChatSessionReference(session.getId(), session.getTitle()));
            if (request.getAssistantMessageId() != null) {
                reply = chatMessageRepository.findByIdAndSessionId(request.getAssistantMessageId(), session.getId())
                        .map(ChatMessage::getContent).orElse(null);
            }
            if (request.getSourceMessageId() != null) {
                proposals = workspaceChangeProposalService.listForMessage(session.getId(), request.getSourceMessageId())
                        .stream().filter(proposal -> AppRoles.isSuperAdmin(actor.getGlobalRole())
                                || projectAccessService.hasProjectRole(proposal.projectId(), actor.getId(), ProjectRoles.VIEWER))
                        .toList();
            }
        }
        String state = "COMPLETED".equals(request.getStatus()) && !proposals.isEmpty()
                ? "REVIEW_REQUIRED" : request.getStatus();
        return new AgentMessageResponse(request.getId(), reply, routing, state, proposals, request.getLastError());
    }

    void route(AgentMessageRoute agentMessageRoute) {
        AppUser actor = requireActiveActor(agentMessageRoute.getUserId());
        LlmService llmService = requireLlmService();
        List<ChatSessionCandidate> chatSessionCandidates = chatSessionCandidates(actor);
        RouteSelection routeSelection = selectChatSession(
                agentMessageRoute.getMessage(), chatSessionCandidates, actor, llmService);
        inOwnedRequest(agentMessageRoute, "ROUTING", () -> {
            String chatSessionId;
            String routingDecision;
            if (routeSelection.create()) {
                ChatSessionView createdChatSession = chatService.createSession(actor.getId(), actor);
                chatSessionId = createdChatSession.id();
                routingDecision = "CREATE";
            } else {
                ChatSession selectedChatSession = chatService.getSessionForChat(
                        routeSelection.chatSessionId(), actor.getId());
                chatSessionId = selectedChatSession.getId();
                routingDecision = chatSessionCandidates.getFirst().chatSession().getId().equals(chatSessionId)
                        ? "CONTINUE" : "SWITCH";
            }

            List<Project> mentionedProjects = mentionedProjects(actor, agentMessageRoute.getMessage());
            for (Project project : mentionedProjects) {
                chatService.addContext(chatSessionId, actor.getId(), actor, "PROJECT", project.getId());
            }
            ChatMessage sourceMessage = chatService.addMessage(
                    chatSessionId, "user", agentMessageRoute.getMessage());
            String[] contextIds = chatService.contextsForChat(chatSessionId, actor.getId(), actor).stream()
                    .map(ChatSessionContext::getId).toArray(String[]::new);
            requireUpdated(agentMessageRequestRepository.markRouted(
                    agentMessageRoute.getId(), chatSessionId, routingDecision, sourceMessage.getId(), contextIds));
            return null;
        });
    }

    void execute(AgentMessageRoute request) {
        AppUser actor = requireActiveActor(request.getUserId());
        LlmService llmService = requireLlmService();
        ChatSession chatSession = chatService.getSessionForChat(request.getRoutedChatSessionId(), actor.getId());
        ChatMessage source = chatMessageRepository.findByIdAndSessionId(
                request.getSourceMessageId(), request.getRoutedChatSessionId())
                .orElseThrow(() -> new IllegalStateException("Source message not found"));
        Set<String> contextIds = Set.copyOf(Arrays.asList(request.getContextIds()));
        List<ChatSessionContext> storedContexts = chatService.contextsForChat(
                chatSession.getId(), actor.getId(), actor).stream()
                .filter(context -> contextIds.contains(context.getId())).toList();
        List<ChatSessionContextView> contextViews = chatService.listContexts(
                chatSession.getId(), actor.getId(), actor).stream()
                .filter(context -> contextIds.contains(context.id())).toList();
        List<String> projectIds = projectIdsForContexts(storedContexts);
        List<Project> contextProjects = requireContextProjects(projectIds, actor);
        Project targetProject = targetProject(
                mentionedProjects(actor, request.getMessage()).stream()
                        .filter(project -> projectIds.contains(project.getId())).toList(), contextProjects, actor);
        String usageProjectId = targetProject == null
                ? (contextProjects.isEmpty() ? null : contextProjects.getFirst().getId())
                : targetProject.getId();
        ToolExecutionContext toolContext = new ToolExecutionContext(actor, chatSession.getId(), projectIds);
        List<LlmTool<?>> availableTools = new ArrayList<>(toolRegistry.llmTools(toolContext));
        availableTools.add(findProjectsTool.forContext(toolContext));
        if (targetProject != null) {
            availableTools.add(guardProposalTool(request, proposeWorkspaceChangesTool.forMessage(toolContext, targetProject.getId(),
                    chatSession.getId(), source.getId(),
                    source.getContent())));
        }

        List<LlmMessage> messageHistory = boundedHistory(chatMessageRepository.findForAgentRequest(
                chatSession.getId(), request.getIngestionSequence(),
                source.getCreatedAt(), MAX_HISTORY_MESSAGES));
        long startNanos = System.nanoTime();
        LlmResult<String> llmResult;
        try {
            llmResult = llmService.runChatWithTools(messageHistory,
                    instructions(targetProject, chatSession, source,
                            selectedContext(contextProjects, contextViews), projectIds),
                    availableTools);
        } catch (RuntimeException exception) {
            llmUsageService.recordFailure(new LlmUsageContext(actor.getId(), usageProjectId, LlmUsageFeature.CHAT),
                    exception.getMessage(), elapsedMillis(startNanos));
            throw exception;
        }
        llmUsageService.record(new LlmUsageContext(actor.getId(), usageProjectId, LlmUsageFeature.CHAT),
                llmResult, elapsedMillis(startNanos));
        inOwnedRequest(request, "PROCESSING", () -> {
            ChatMessage assistantMessage = chatService.addMessage(
                    chatSession.getId(), "assistant", llmResult.output());
            requireUpdated(agentMessageRequestRepository.markCompleted(request.getId(), assistantMessage.getId()));
            return null;
        });
    }

    private AppUser requireActiveActor(String userId) {
        return appUserRepository.findById(userId)
                .filter(actor -> "ACTIVE".equalsIgnoreCase(actor.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not active"));
    }

    private LlmService requireLlmService() {
        LlmService service = llmServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI chat is unavailable");
        }
        return service;
    }

    private <T> T inOwnedRequest(AgentMessageRoute request, String status, Supplier<T> action) {
        return transactions.execute(transaction -> {
            agentMessageRequestRepository.lockOwned(request.getId(), request.getUserId(), status)
                    .orElseThrow(() -> new IllegalStateException("Agent message is no longer owned by this worker"));
            return action.get();
        });
    }

    private <T> LlmTool<T> guardProposalTool(AgentMessageRoute request, LlmTool<T> tool) {
        return new LlmTool<>(tool.name(), tool.description(), tool.parametersType(), parameters ->
                inOwnedRequest(request, "PROCESSING", () -> {
                    // The ownership check and proposal insert commit together, including when
                    // the LLM invokes this tool on a different executor thread.
                    try {
                        return tool.handler().execute(parameters);
                    } catch (RuntimeException exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw new LlmException("Proposal creation failed", exception);
                    }
                }));
    }

    private List<ChatSessionCandidate> chatSessionCandidates(AppUser actor) {
        List<ChatSessionCandidate> chatSessionCandidates = new ArrayList<>();
        List<ChatSession> sessions = new ArrayList<>();
        agentMessageRequestRepository.findLastRoutedSession(actor.getId())
                .flatMap(id -> chatSessionRepository.findByIdAndUserId(id, actor.getId()))
                .filter(session -> "ACTIVE".equals(session.getStatus())).ifPresent(sessions::add);
        chatSessionRepository.findRecentActiveByUserId(actor.getId(), MAX_CANDIDATE_CHAT_SESSIONS).stream()
                .filter(session -> sessions.stream().noneMatch(existing -> existing.getId().equals(session.getId())))
                .limit(MAX_CANDIDATE_CHAT_SESSIONS - sessions.size()).forEach(sessions::add);
        for (ChatSession chatSession : sessions) {
            List<ChatMessage> recentMessages = chatMessageRepository.findRecentBySessionId(
                    chatSession.getId(), MAX_CANDIDATE_MESSAGES);
            if (recentMessages.isEmpty()) continue;
            List<ChatSessionContextView> chatSessionContexts = chatService.listContexts(
                            chatSession.getId(), actor.getId(), actor)
                    .stream().limit(5).toList();
            chatSessionCandidates.add(new ChatSessionCandidate(
                    chatSession, recentMessages, chatSessionContexts));
        }
        return List.copyOf(chatSessionCandidates);
    }

    private RouteSelection selectChatSession(String message, List<ChatSessionCandidate> chatSessionCandidates,
                                             AppUser actor, LlmService llmService) {
        if (chatSessionCandidates.isEmpty()) return new RouteSelection(true, null);
        AtomicReference<RouteToolParameters> routeSelectionReference = new AtomicReference<>();
        LlmTool<RouteToolParameters> routingTool = new LlmTool<>(
                "route_agent_message",
                "Choose whether the incoming message belongs to an existing candidate chat session or needs a new chat session. "
                        + "Use decision CONTINUE for the current chat session, SWITCH for another candidate, or CREATE for a new topic. "
                        + "For CONTINUE or SWITCH, provide an exact candidate chatSessionId. For CREATE, omit chatSessionId.",
                RouteToolParameters.class,
                parameters -> {
                    if (!routeSelectionReference.compareAndSet(null, parameters)) {
                        throw new LlmException("AI returned more than one routing decision");
                    }
                    return Map.of("accepted", true);
                });
        long startNanos = System.nanoTime();
        LlmResult<String> routingLlmResult;
        try {
            routingLlmResult = llmService.runChatWithTools(
                    List.of(new LlmMessage("user", routingInput(message, chatSessionCandidates))),
                    FileUtils.loadSystemPrompt("agent-message-routing.md"),
                    List.of(routingTool));
        } catch (RuntimeException exception) {
            llmUsageService.recordFailure(new LlmUsageContext(actor.getId(), null, LlmUsageFeature.CHAT),
                    exception.getMessage(), elapsedMillis(startNanos));
            throw exception;
        }
        llmUsageService.record(new LlmUsageContext(actor.getId(), null, LlmUsageFeature.CHAT),
                routingLlmResult, elapsedMillis(startNanos));
        RouteToolParameters routeToolParameters = routeSelectionReference.get();
        if (routeToolParameters == null || routeToolParameters.decision() == null) {
            throw new LlmException("AI did not return a routing decision");
        }
        String routingDecision = routeToolParameters.decision().trim().toUpperCase(Locale.ROOT);
        if ("CREATE".equals(routingDecision)) return new RouteSelection(true, null);
        if (!List.of("CONTINUE", "SWITCH").contains(routingDecision)
                || routeToolParameters.chatSessionId() == null
                || chatSessionCandidates.stream().noneMatch(candidate -> candidate.chatSession().getId()
                .equals(routeToolParameters.chatSessionId()))) {
            throw new LlmException("AI returned an invalid routing decision");
        }
        return new RouteSelection(false, routeToolParameters.chatSessionId());
    }

    private String routingInput(String message, List<ChatSessionCandidate> chatSessionCandidates) {
        StringBuilder input = new StringBuilder("Incoming message:\n").append(message)
                .append("\n\nCandidate chat sessions, newest first. The first candidate is the current chat session:\n");
        for (ChatSessionCandidate candidate : chatSessionCandidates) {
            input.append("\nChat session id: ").append(candidate.chatSession().getId())
                    .append("\nTitle: ").append(Objects.toString(candidate.chatSession().getTitle(), "Untitled"));
            if (!candidate.contexts().isEmpty()) {
                input.append("\nContext:");
                candidate.contexts().forEach(context -> input.append("\n- ")
                        .append(context.entityType()).append(": ").append(bounded(context.label(), 120)));
            }
            input.append("\nRecent messages:");
            candidate.recentMessages().forEach(candidateMessage -> input.append("\n- ")
                    .append(candidateMessage.getRole()).append(": ")
                    .append(bounded(candidateMessage.getContent(), 500)));
            input.append('\n');
        }
        return input.toString();
    }

    private List<Project> mentionedProjects(AppUser actor, String message) {
        return AppRoles.isSuperAdmin(actor.getGlobalRole())
                ? projectRepository.findMentionedInText(message, MAX_MENTIONED_PROJECTS)
                : projectRepository.findVisibleMentionedInText(
                        actor.getId(), message, MAX_MENTIONED_PROJECTS);
    }

    private List<String> projectIdsForContexts(List<ChatSessionContext> contexts) {
        LinkedHashSet<String> projectIds = new LinkedHashSet<>();
        for (ChatSessionContext context : contexts) {
            if ("PROJECT".equals(context.getEntityType())) projectIds.add(context.getEntityId());
            if ("WORK_ITEM".equals(context.getEntityType())) {
                WorkItem item = workItemRepository.findById(context.getEntityId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
                projectIds.add(item.getProjectId());
            }
        }
        if (projectIds.size() > MAX_CONTEXT_PROJECTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A maximum of 10 projects can be used as context");
        }
        return List.copyOf(projectIds);
    }

    private List<Project> requireContextProjects(List<String> projectIds, AppUser actor) {
        return projectIds.stream().map(id -> {
            projectAccessService.requireProjectRole(id, actor, ProjectRoles.VIEWER);
            return projectRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        }).toList();
    }

    private Project targetProject(List<Project> mentionedProjects,
                                  List<Project> contextProjects, AppUser actor) {
        Project candidateProject = mentionedProjects.size() == 1
                ? mentionedProjects.getFirst()
                : (contextProjects.size() == 1 ? contextProjects.getFirst() : null);
        if (candidateProject == null) return null;
        if (AppRoles.isSuperAdmin(actor.getGlobalRole())
                || projectAccessService.hasProjectRole(
                        candidateProject.getId(), actor.getId(), ProjectRoles.EDITOR)) {
            return candidateProject;
        }
        return null;
    }

    private String selectedContext(List<Project> contextProjects, List<ChatSessionContextView> contextViews) {
        StringBuilder context = new StringBuilder();
        if (contextProjects.isEmpty()) {
            context.append("No project is currently resolved. Use find_projects when a project-scoped request is ambiguous.");
        } else {
            context.append("Resolved project context (lightweight references only; fetch details with read tools):\n");
            contextProjects.forEach(project -> context.append("- Project: ").append(project.getName())
                    .append(" [id=").append(project.getId()).append("]\n"));
        }
        if (!contextViews.isEmpty()) {
            context.append("\nPersisted chat session context:\n");
            contextViews.forEach(item -> context.append("- ").append(item.entityType()).append(": ")
                    .append(item.label()).append(" [id=").append(item.entityId()).append("]\n"));
        }
        return context.toString().trim();
    }

    private String instructions(Project targetProject, ChatSession chatSession, ChatMessage sourceMessage,
                                String selectedContext, List<String> projectIds) {
        return FileUtils.loadSystemPrompt("chat-instructions.md")
                .replace("{{projectName}}", targetProject == null ? "None" : Objects.toString(targetProject.getName(), "Untitled project"))
                .replace("{{projectId}}", targetProject == null ? "" : targetProject.getId())
                .replace("{{projectIds}}", String.join(", ", projectIds))
                .replace("{{chatSessionId}}", chatSession.getId())
                .replace("{{sourceMessageId}}", sourceMessage.getId())
                .replace("{{selectedContext}}", selectedContext)
                + "\n\n<universal_agent_input>\n"
                + "This request came through the universal agent input. The backend manages chat session and project context. "
                + "Never ask the user to add or select chat context. If find_projects cannot resolve one clear project, "
                + "ask only for the project name or a more specific identifier; the next message will be routed and scoped automatically.\n"
                + "</universal_agent_input>";
    }

    private List<LlmMessage> boundedHistory(List<ChatMessage> storedMessages) {
        List<LlmMessage> selectedMessages = new ArrayList<>();
        int totalLength = 0;
        for (int index = storedMessages.size() - 1; index >= 0; index--) {
            ChatMessage message = storedMessages.get(index);
            if (!List.of("user", "assistant").contains(message.getRole())) continue;
            if (!selectedMessages.isEmpty()
                    && totalLength + message.getContent().length() > MAX_HISTORY_LENGTH) break;
            selectedMessages.add(new LlmMessage(message.getRole(), message.getContent()));
            totalLength += message.getContent().length();
        }
        java.util.Collections.reverse(selectedMessages);
        return List.copyOf(selectedMessages);
    }

    private String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is required");
        }
        String normalized = message.trim();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must be 20000 characters or fewer");
        }
        return normalized;
    }

    private String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
        }
        String normalized = key.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be 200 characters or fewer");
        }
        return normalized;
    }

    private long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private String bounded(String value, int limit) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit - 3) + "...";
    }

    private void requireUpdated(int rows) {
        if (rows != 1) throw new IllegalStateException("Agent message request could not be updated");
    }

    private record ChatSessionCandidate(ChatSession chatSession, List<ChatMessage> recentMessages,
                                        List<ChatSessionContextView> contexts) { }
    private record RouteSelection(boolean create, String chatSessionId) { }
    record RouteToolParameters(String decision, String chatSessionId) { }
}
