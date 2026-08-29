package com.windrunner.server.chat.api;

import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.domain.UserContext;
import com.windrunner.server.chat.ChatService;
import com.windrunner.server.chat.domain.ChatMessage;
import com.windrunner.server.chat.domain.ChatSession;
import com.windrunner.server.chat.domain.ChatSessionContext;
import com.windrunner.server.llm.*;
import com.windrunner.server.llm.domain.LlmUsageFeature;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.tools.ToolRegistry;
import com.windrunner.server.tools.work.FetchEntriesTool;
import com.windrunner.server.tools.work.FetchRelationshipsTool;
import com.windrunner.server.tools.work.FetchWorkItemsTool;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.EntryService;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.WorkspaceChangeProposalService;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import com.windrunner.server.work.persistence.WorkItemRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/chat-sessions/{sessionId}/messages")
public class ChatMessageController {
    private static final long STREAM_TIMEOUT_MILLIS = 180_000;
    private static final int MAX_MESSAGES = 50;
    private static final int MAX_MESSAGE_LENGTH = 20_000;
    private static final int MAX_TOTAL_LENGTH = 100_000;
    private static final int MAX_CONTEXT_PROJECTS = 10;
    private static final Set<String> ALLOWED_ROLES = Set.of("user", "assistant");

    private final ObjectProvider<LlmService> llmServiceProvider;
    private final ProjectRepository projects;
    private final ToolRegistry toolRegistry;
    private final AuthService authService;
    private final ProjectAccessService projectAccessService;
    private final ChatService chatService;
    private final WorkItemService workItems;
    private final WorkItemRepository workItemRepository;
    private final EntryService entries;
    private final WorkspaceChangeProposalService workspaceChangeProposals;
    private final LlmUsageService llmUsageService;

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@PathVariable String sessionId,
                                 @RequestBody ChatRequest request,
                                 HttpServletRequest httpRequest) {
        AppUser actor = authService.requireCurrentUser(httpRequest);
        UserContext user = authService.requireUserContext(httpRequest);
        ChatSession session = chatService.getSessionForChat(sessionId, user.userId());
        LlmService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI chat is unavailable");

        List<LlmMessage> messages = validateMessages(request);
        persistRequestedProjectContexts(session, user, actor, request == null ? null : request.projectIds());
        List<ChatSessionContext> sessionContexts = chatService.contextsForChat(session.getId(), user.userId(), actor);
        List<ChatSessionContextView> contextViews = chatService.listContexts(session.getId(), user.userId(), actor);
        List<String> contextProjectIds = projectIdsForContexts(sessionContexts);
        if (request != null && request.targetProjectId() != null && !request.targetProjectId().isBlank()) {
            String targetProjectId = request.targetProjectId().trim();
            projectAccessService.requireProjectRole(targetProjectId, actor, ProjectRoles.EDITOR);
            if (!contextProjectIds.contains(targetProjectId)) contextProjectIds = appendProject(contextProjectIds, targetProjectId);
        }
        List<Project> contextProjects = requireContextProjects(contextProjectIds, actor);
        ProjectChatContext requestedContext = request == null ? null : request.context();
        boolean hasSelectedWorkItem = requestedContext != null
                && requestedContext.selectedNodeId() != null
                && !requestedContext.selectedNodeId().isBlank();
        String context = hasSelectedWorkItem
                ? selectedWorkItemContext(targetProjectId(request), requestedContext)
                : (contextProjects.isEmpty() ? selectedWorkItemContext(targetProjectId(request), requestedContext) : selectedProjectContext(contextProjects));
        context = appendGenericContexts(context, contextViews);
        String targetProjectId = request == null ? null : blankToNull(request.targetProjectId());
        if (targetProjectId == null && contextProjects.size() == 1) targetProjectId = contextProjects.getFirst().getId();
        Project targetProject = targetProjectId == null ? null : projects.findById(targetProjectId).orElse(null);
        ChatMessage sourceMessage = chatService.addMessage(session.getId(), "user", messages.getLast().content());
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        String usageProjectId = targetProject == null ? (contextProjects.isEmpty() ? null : contextProjects.getFirst().getId()) : targetProject.getId();
        final List<String> allowedProjectIds = contextProjectIds;
        final String promptContext = context;

        Thread.startVirtualThread(() -> {
            long startNanos = System.nanoTime();
            try {
                List<LlmTool<?>> availableTools = new ArrayList<>(projectScopedTools(allowedProjectIds));
                if (targetProject != null) availableTools.add(workspaceProposalTool(targetProject.getId(), session, sourceMessage));
                LlmResult<String> llmResult = llmService.runChatWithTools(
                        messages,
                        instructions(targetProject, session, sourceMessage, promptContext, allowedProjectIds),
                        availableTools);
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                llmUsageService.record(new LlmUsageContext(actor.getId(), usageProjectId, LlmUsageFeature.CHAT), llmResult, durationMs);
                ChatMessage assistantMessage = chatService.addMessage(session.getId(), "assistant", llmResult.output());
                send(emitter, "delta", new ChatDelta(llmResult.output()));
                send(emitter, "done", new ChatDone(session.getId(), sourceMessage.getId(), assistantMessage.getId()));
                emitter.complete();
            } catch (Exception exception) {
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                llmUsageService.recordFailure(new LlmUsageContext(actor.getId(), usageProjectId, LlmUsageFeature.CHAT), exception.getMessage(), durationMs);
                log.warn("Global chat failed for sessionId={}", session.getId(), exception);
                try { send(emitter, "error", new ChatError(userFacingMessage(exception))); emitter.complete(); }
                catch (Exception ignored) { emitter.completeWithError(exception); }
            }
        });
        return emitter;
    }

    private void persistRequestedProjectContexts(ChatSession session, UserContext user, AppUser actor, List<String> projectIds) {
        if (projectIds == null) return;
        for (String projectId : new LinkedHashSet<>(projectIds)) {
            if (projectId != null && !projectId.isBlank()) chatService.addContext(session.getId(), user.userId(), actor, "PROJECT", projectId.trim());
        }
    }

    private List<String> projectIdsForContexts(List<ChatSessionContext> contexts) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (ChatSessionContext context : contexts) {
            if ("PROJECT".equals(context.getEntityType())) ids.add(context.getEntityId());
            if ("WORK_ITEM".equals(context.getEntityType())) {
                WorkItem item = workItemRepository.findById(context.getEntityId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
                ids.add(item.getProjectId());
            }
        }
        if (ids.size() > MAX_CONTEXT_PROJECTS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A maximum of 10 projects can be used as context");
        return List.copyOf(ids);
    }

    private List<String> appendProject(List<String> projectIds, String projectId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(projectIds);
        ids.add(projectId);
        if (ids.size() > MAX_CONTEXT_PROJECTS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A maximum of 10 projects can be used as context");
        return List.copyOf(ids);
    }

    private String appendGenericContexts(String context, List<ChatSessionContextView> contextViews) {
        if (contextViews.isEmpty()) return context;
        StringBuilder result = new StringBuilder(context).append("\n\nPersisted conversation context:\n");
        contextViews.forEach(item -> result.append("- ").append(item.entityType()).append(": ").append(item.label())
                .append(" [id=").append(item.entityId()).append("]\n"));
        return result.toString().trim();
    }

    private String targetProjectId(ChatRequest request) {
        return request == null ? null : request.targetProjectId();
    }

    private List<LlmMessage> validateMessages(ChatRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one chat message is required");
        if (request.messages().size() > MAX_MESSAGES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat history is too long");
        int totalLength = 0;
        for (LlmMessage message : request.messages()) {
            if (message == null || !ALLOWED_ROLES.contains(message.role()) || message.content() == null || message.content().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat messages require a user or assistant role and content");
            if (message.content().length() > MAX_MESSAGE_LENGTH) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A chat message is too long");
            totalLength += message.content().length();
        }
        if (totalLength > MAX_TOTAL_LENGTH) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat history is too large");
        if (!"user".equals(request.messages().getLast().role())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The final chat message must be from the user");
        return List.copyOf(request.messages());
    }

    private List<Project> requireContextProjects(List<String> projectIds, AppUser actor) {
        return projectIds.stream().map(id -> {
            projectAccessService.requireProjectRole(id, actor, ProjectRoles.VIEWER);
            return projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        }).toList();
    }

    private List<LlmTool<?>> projectScopedTools(List<String> allowedProjectIds) {
        return toolRegistry.llmTools().stream().map(tool -> switch (tool.name()) {
            case "fetch_work_items", "fetch_entries", "fetch_relationships" -> scopedProjectTool(tool, allowedProjectIds);
            default -> tool;
        }).toList();
    }

    private <T> LlmTool<T> scopedProjectTool(LlmTool<T> tool, List<String> allowedProjectIds) {
        return new LlmTool<>(tool.name(), tool.description(), tool.parametersType(), arguments -> {
            String requestedProjectId = projectIdFromToolArguments(arguments);
            if (!allowedProjectIds.contains(requestedProjectId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The requested project is not in the selected context");
            return tool.handler().execute(arguments);
        });
    }

    private String projectIdFromToolArguments(Object arguments) {
        if (arguments instanceof FetchWorkItemsTool.Parameters p) return p.projectId();
        if (arguments instanceof FetchEntriesTool.Parameters p) return p.projectId();
        if (arguments instanceof FetchRelationshipsTool.Parameters p) return p.projectId();
        return null;
    }

    private String selectedProjectContext(List<Project> selectedProjects) {
        StringBuilder scope = new StringBuilder("Selected project context (each project is a separate source; includes current WorkItems and updates):\n");
        for (Project project : selectedProjects) {
            List<WorkItem> allWorkItems = workItems.list(project.getId());
            List<Entry> allEntries = entries.list(project.getId());
            Map<String, List<WorkItemAssignee>> assignees = assigneesByWorkItemId(allWorkItems);
            scope.append("\nProject: ").append(project.getName()).append(" [id=").append(project.getId()).append("]\n");
            allWorkItems.stream().filter(item -> item.getParentWorkItemId() == null)
                    .sorted(Comparator.<WorkItem>comparingInt(item -> item.getSortIndex() == null ? Integer.MAX_VALUE : item.getSortIndex()).thenComparing(WorkItem::getId))
                    .forEach(item -> appendWorkItemScope(item, allWorkItems, allEntries, assignees, 0, scope));
            if (allWorkItems.isEmpty()) scope.append("  No WorkItems.\n");
        }
        return scope.toString().trim();
    }

    private String selectedWorkItemContext(String projectId, ProjectChatContext context) {
        if (projectId == null || projectId.isBlank() || context == null || context.selectedNodeId() == null || context.selectedNodeId().isBlank()) return "No specific artifact is selected. Use the available read tools to find relevant workspace records, and ask a concise clarification when the request is ambiguous.";
        WorkItem item = workItems.get(projectId, context.selectedNodeId().trim());
        StringBuilder scope = new StringBuilder("Selected WorkItem scope (includes every nested WorkItem and update):\n");
        List<WorkItem> allWorkItems = workItems.list(projectId);
        appendWorkItemScope(item, allWorkItems, entries.list(projectId), assigneesByWorkItemId(allWorkItems), 0, scope);
        return scope.toString().trim();
    }

    private Map<String, List<WorkItemAssignee>> assigneesByWorkItemId(List<WorkItem> allWorkItems) {
        Map<String, List<WorkItemAssignee>> result = new HashMap<>();
        workItems.views(allWorkItems).forEach(view -> result.put(view.workItem().getId(), view.assignees()));
        return result;
    }

    private void appendWorkItemScope(WorkItem item, List<WorkItem> allWorkItems, List<Entry> allEntries, Map<String, List<WorkItemAssignee>> assignees, int depth, StringBuilder scope) {
        String indent = "  ".repeat(depth);
        scope.append(indent).append("- WorkItem: ").append(item.getTitle()).append(" [id=").append(item.getId()).append(", type=").append(item.getType()).append(", status=").append(item.getStatus()).append(", priority=").append(Objects.toString(item.getPriority(), "Not set")).append(", due date=").append(Objects.toString(item.getDueDate(), "Not set")).append(", assignees=").append(assignees.getOrDefault(item.getId(), List.of())).append("]\n");
        allEntries.stream().filter(entry -> item.getId().equals(entry.getWorkItemId())).forEach(entry -> scope.append(indent).append("  - Update: ").append(entrySummary(entry)).append('\n'));
        allWorkItems.stream().filter(child -> item.getId().equals(child.getParentWorkItemId())).sorted(Comparator.comparingInt(child -> child.getSortIndex() == null ? Integer.MAX_VALUE : child.getSortIndex())).forEach(child -> appendWorkItemScope(child, allWorkItems, allEntries, assignees, depth + 1, scope));
    }

    private String entrySummary(Entry entry) { return "[type=" + entry.getType() + ", updated=" + Objects.toString(entry.getUpdatedAt(), "Unknown") + "] " + entry.getBody(); }

    private String instructions(Project targetProject, ChatSession session, ChatMessage sourceMessage, String selectedContext, List<String> contextProjectIds) {
        return FileUtils.loadSystemPrompt("project-chat-instructions.md")
                .replace("{{projectName}}", targetProject == null ? "the selected workspace context" : Objects.toString(targetProject.getName(), "the selected workspace context"))
                .replace("{{projectId}}", targetProject == null ? "" : Objects.toString(targetProject.getId(), ""))
                .replace("{{projectIds}}", String.join(", ", contextProjectIds))
                .replace("{{chatSessionId}}", session.getId())
                .replace("{{sourceMessageId}}", sourceMessage.getId())
                .replace("{{selectedContext}}", selectedContext);
    }

    private LlmTool<WorkspaceChangeProposalService.ProposalDraft> workspaceProposalTool(String projectId, ChatSession session, ChatMessage sourceMessage) {
        return new LlmTool<>("propose_workspace_changes", FileUtils.loadSystemPrompt("propose-workspace-changes-tool.md"), WorkspaceChangeProposalService.ProposalDraft.class,
                draft -> workspaceChangeProposals.create(projectId, session.getId(), sourceMessage.getId(), sourceMessage.getContent(), draft));
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void send(SseEmitter emitter, String eventName, Object data) throws IOException { emitter.send(SseEmitter.event().name(eventName).data(data)); }
    private String userFacingMessage(Exception exception) { return exception.getMessage() == null || exception.getMessage().isBlank() ? "The chat response could not be completed" : exception.getMessage(); }

    public record ChatRequest(List<LlmMessage> messages, ProjectChatContext context, List<String> projectIds, String targetProjectId) { }
    public record ProjectChatContext(String selectedNodeId, String selectedProposalId, String selectedProposalChangeId) { }
    public record ChatDelta(String text) { }
    public record ChatDone(String chatSessionId, String sourceMessageId, String assistantMessageId) { }
    public record ChatError(String message) { }
}
