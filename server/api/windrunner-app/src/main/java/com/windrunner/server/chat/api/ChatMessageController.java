package com.windrunner.server.chat.api;

import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.domain.UserContext;
import com.windrunner.server.chat.ChatService;
import com.windrunner.server.chat.domain.ChatMessage;
import com.windrunner.server.chat.domain.ChatSession;
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
import com.windrunner.server.tools.ToolRegistry;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.EntryService;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.WorkspaceChangeProposalService;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Handles the conversational inspector.  The response is delivered as SSE so the UI can
 * retain its streaming contract even though the current provider returns a completed answer. */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/projects/{projectId}/chat-messages")
public class ChatMessageController {

    private static final long STREAM_TIMEOUT_MILLIS = 180_000;
    private static final int MAX_MESSAGES = 50;
    private static final int MAX_MESSAGE_LENGTH = 20_000;
    private static final int MAX_TOTAL_LENGTH = 100_000;
    private static final Set<String> ALLOWED_ROLES = Set.of("user", "assistant");
    private static final String PROJECT_CHAT_INSTRUCTIONS_PROMPT = "project-chat-instructions.md";

    private final ObjectProvider<LlmService> llmServiceProvider;
    private final ProjectRepository projects;
    private final ToolRegistry toolRegistry;
    private final AuthService authService;
    private final ProjectAccessService projectAccessService;
    private final ChatService chatService;
    private final WorkItemService workItems;
    private final EntryService entries;
    private final WorkspaceChangeProposalService workspaceChangeProposals;
    private final LlmUsageService llmUsageService;

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @PathVariable("projectId") String projectId,
            @RequestBody ProjectChatRequest request,
            HttpServletRequest httpRequest
    ) {
        AppUser actor = authService.requireCurrentUser(httpRequest);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.EDITOR);
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        LlmService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI chat is unavailable");
        }

        List<LlmMessage> messages = validateMessages(request);
        UserContext user = authService.requireUserContext(httpRequest);
        ChatSession session = chatService.getOrCreateActiveSession(projectId, user.userId());
        ChatMessage sourceMessage = chatService.addMessage(session.getId(), "user", messages.getLast().content());
        String context = selectedWorkItemContext(projectId, request.context());
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        Thread.startVirtualThread(() -> {
            long startNanos = System.nanoTime();
            try {
                List<LlmTool<?>> availableTools = new ArrayList<>(toolRegistry.llmTools());
                availableTools.add(workspaceProposalTool(projectId, session, sourceMessage));
                LlmResult<String> llmResult = llmService.runChatWithTools(
                        messages,
                        instructions(project, session, sourceMessage, context),
                        availableTools
                );
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                llmUsageService.record(
                        new LlmUsageContext(actor.getId(), projectId, LlmUsageFeature.CHAT),
                        llmResult,
                        durationMs);
                String answer = llmResult.output();
                ChatMessage assistantMessage = chatService.addMessage(session.getId(), "assistant", answer);
                send(emitter, "delta", new ChatDelta(answer));
                send(emitter, "done", new ChatDone(session.getId(), sourceMessage.getId(), assistantMessage.getId()));
                emitter.complete();
            } catch (Exception exception) {
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                llmUsageService.recordFailure(
                        new LlmUsageContext(actor.getId(), projectId, LlmUsageFeature.CHAT),
                        exception.getMessage(),
                        durationMs);
                log.warn("Project chat failed for projectId={}", projectId, exception);
                try {
                    send(emitter, "error", new ChatError(userFacingMessage(exception)));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(exception);
                }
            }
        });
        return emitter;
    }

    private List<LlmMessage> validateMessages(ProjectChatRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one chat message is required");
        }
        if (request.messages().size() > MAX_MESSAGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat history is too long");
        }
        int totalLength = 0;
        for (LlmMessage message : request.messages()) {
            if (message == null || !ALLOWED_ROLES.contains(message.role()) || message.content() == null || message.content().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat messages require a user or assistant role and content");
            }
            if (message.content().length() > MAX_MESSAGE_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A chat message is too long");
            }
            totalLength += message.content().length();
        }
        if (totalLength > MAX_TOTAL_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat history is too large");
        }
        if (!"user".equals(request.messages().getLast().role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The final chat message must be from the user");
        }
        return List.copyOf(request.messages());
    }

    private String selectedWorkItemContext(String projectId, ProjectChatContext context) {
        if (context == null || context.selectedNodeId() == null || context.selectedNodeId().isBlank()) {
            return "No WorkItem is selected. Creation requests may start at project level. For a read or update request that depends on one specific existing WorkItem, find an unambiguous match with the read tools or ask the user to select it.";
        }
        WorkItem item = workItems.get(projectId, context.selectedNodeId().trim());
        StringBuilder scope = new StringBuilder("Selected WorkItem scope (includes every nested WorkItem and update):\n");
        appendWorkItemScope(item, workItems.list(projectId), entries.list(projectId), 0, scope);
        return scope.toString().trim();
    }

    private void appendWorkItemScope(
            WorkItem item,
            List<WorkItem> allWorkItems,
            List<Entry> allEntries,
            int depth,
            StringBuilder scope
    ) {
        String indent = "  ".repeat(depth);
        List<WorkItemAssignee> assignees = workItems.assignees(item.getId());
        scope.append(indent).append("- WorkItem: ").append(item.getTitle())
                .append(" [id=").append(item.getId())
                .append(", type=").append(item.getType())
                .append(", status=").append(item.getStatus())
                .append(", priority=").append(Objects.toString(item.getPriority(), "Not set"))
                .append(", due date=").append(Objects.toString(item.getDueDate(), "Not set"))
                .append(", assignees=").append(assignees)
                .append("]\n");

        List<ScopeContent> content = new ArrayList<>();
        allEntries.stream()
                .filter(entry -> item.getId().equals(entry.getWorkItemId()))
                .forEach(entry -> content.add(ScopeContent.forEntry(entry)));
        allWorkItems.stream()
                .filter(child -> item.getId().equals(child.getParentWorkItemId()))
                .forEach(child -> content.add(ScopeContent.forWorkItem(child)));
        content.stream()
                .sorted(Comparator.comparingInt(ScopeContent::sortIndex).thenComparing(ScopeContent::stableId))
                .forEach(next -> {
                    if (next.entry() != null) {
                        scope.append(indent).append("  - Update: ").append(entrySummary(next.entry())).append('\n');
                    } else {
                        appendWorkItemScope(next.workItem(), allWorkItems, allEntries, depth + 1, scope);
                    }
                });
    }

    private String entrySummary(Entry entry) {
        return "[type=" + entry.getType() + ", updated=" + Objects.toString(entry.getUpdatedAt(), "Unknown") + "] " + entry.getBody();
    }

    private String instructions(Project project, ChatSession session, ChatMessage sourceMessage, String selectedContext) {
        return FileUtils.loadSystemPrompt(PROJECT_CHAT_INSTRUCTIONS_PROMPT)
                .replace("{{projectName}}", Objects.toString(project.getName(), ""))
                .replace("{{projectId}}", Objects.toString(project.getId(), ""))
                .replace("{{chatSessionId}}", Objects.toString(session.getId(), ""))
                .replace("{{sourceMessageId}}", Objects.toString(sourceMessage.getId(), ""))
                .replace("{{selectedContext}}", selectedContext);
    }

    private LlmTool<WorkspaceChangeProposalService.ProposalDraft> workspaceProposalTool(
            String projectId, ChatSession session, ChatMessage sourceMessage) {
        return new LlmTool<>(
                "propose_workspace_changes",
                FileUtils.loadSystemPrompt("propose-workspace-changes-tool.md"),
                WorkspaceChangeProposalService.ProposalDraft.class,
                draft -> workspaceChangeProposals.create(projectId, session.getId(), sourceMessage.getId(), sourceMessage.getContent(), draft)
        );
    }

    private void send(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private String userFacingMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "The chat response could not be completed"
                : exception.getMessage();
    }

    public record ProjectChatRequest(List<LlmMessage> messages, ProjectChatContext context) { }
    public record ProjectChatContext(String selectedNodeId, String selectedProposalId, String selectedProposalChangeId) { }
    public record ChatDelta(String text) { }
    public record ChatDone(String chatSessionId, String sourceMessageId, String assistantMessageId) { }
    public record ChatError(String message) { }
    private record ScopeContent(int sortIndex, String stableId, WorkItem workItem, Entry entry) {
        static ScopeContent forWorkItem(WorkItem item) {
            return new ScopeContent(item.getSortIndex() == null ? Integer.MAX_VALUE : item.getSortIndex(), item.getId(), item, null);
        }

        static ScopeContent forEntry(Entry entry) {
            return new ScopeContent(entry.getSortIndex() == null ? Integer.MAX_VALUE : entry.getSortIndex(), entry.getId(), null, entry);
        }
    }
}
