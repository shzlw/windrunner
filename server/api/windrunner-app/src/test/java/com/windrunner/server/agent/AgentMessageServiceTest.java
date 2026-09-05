package com.windrunner.server.agent;

import com.windrunner.server.agent.domain.AgentMessageRoute;
import com.windrunner.server.agent.persistence.AgentMessageRequestRepository;
import com.windrunner.server.chat.ChatService;
import com.windrunner.server.chat.api.ChatSessionView;
import com.windrunner.server.chat.domain.ChatMessage;
import com.windrunner.server.chat.domain.ChatSession;
import com.windrunner.server.chat.domain.ChatSessionContext;
import com.windrunner.server.chat.api.ChatSessionContextView;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.chat.persistence.ChatMessageRepository;
import com.windrunner.server.chat.persistence.ChatSessionRepository;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.llm.*;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.tools.ToolRegistry;
import com.windrunner.server.tools.chat.FindProjectsTool;
import com.windrunner.server.tools.chat.ProposeWorkspaceChangesTool;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.work.WorkspaceChangeProposalService;
import com.windrunner.server.work.persistence.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentMessageServiceTest {
    @Mock private ObjectProvider<LlmService> llmServiceProvider;
    @Mock private AgentMessageRequestRepository requests;
    @Mock private ChatService chatService;
    @Mock private ChatSessionRepository sessions;
    @Mock private ChatMessageRepository messages;
    @Mock private ProjectRepository projects;
    @Mock private ProjectAccessService projectAccess;
    @Mock private WorkItemRepository workItems;
    @Mock private ToolRegistry tools;
    @Mock private FindProjectsTool findProjects;
    @Mock private ProposeWorkspaceChangesTool proposeChanges;
    @Mock private WorkspaceChangeProposalService proposals;
    @Mock private LlmUsageService usage;
    @Mock private EntityIdGenerator ids;
    @Mock private AppUserRepository users;
    @Mock private TransactionTemplate transactions;
    @Mock private LlmService llm;
    private AgentMessageService service;
    private AppUser actor;

    @BeforeEach
    void setUp() {
        lenient().when(transactions.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        service = new AgentMessageService(llmServiceProvider, requests, chatService, sessions, messages,
                projects, projectAccess, workItems, tools, findProjects, proposeChanges, proposals, usage,
                ids, users, transactions);
        actor = new AppUser();
        actor.setId("user-1");
        actor.setGlobalRole("USER");
        actor.setStatus("ACTIVE");
    }

    @Test
    void acceptsWithoutCallingTheLlmAndReturnsTheSameRequestForADuplicate() {
        AgentMessageRoute request = request("RECEIVED");
        when(ids.generate(any())).thenReturn("new-id");
        when(requests.findByKey("user-1", "key")).thenReturn(Optional.of(request));

        assertThat(service.process(actor, "key", " Hello ").state()).isEqualTo("RECEIVED");
        assertThat(service.process(actor, "key", "Hello").requestId()).isEqualTo("request-1");
        var order = inOrder(requests);
        order.verify(requests).lockIntake("user-1");
        order.verify(requests).insert("new-id", "user-1", "key", "Hello");
        verifyNoInteractions(llmServiceProvider, chatService);
    }

    @Test
    void rejectsAnIdempotencyKeyWithDifferentContent() {
        when(ids.generate(any())).thenReturn("new-id");
        when(requests.findByKey("user-1", "key")).thenReturn(Optional.of(request("RECEIVED")));
        assertThatThrownBy(() -> service.process(actor, "key", "Different"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void statusLookupIsScopedToTheAuthenticatedUser() {
        when(requests.findForUser("other-request", "user-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("other-request", actor))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(chatService, messages, proposals);
    }

    @Test
    void returnsAPersistedCompletedReply() {
        AgentMessageRoute request = request("COMPLETED");
        request.setRoutedChatSessionId("chat-1");
        request.setSourceMessageId("source-1");
        request.setAssistantMessageId("answer-1");
        request.setRoutingDecision("CREATE");
        when(requests.findForUser("request-1", "user-1")).thenReturn(Optional.of(request));
        when(chatService.getSessionForChat("chat-1", "user-1")).thenReturn(session("chat-1"));
        when(messages.findByIdAndSessionId("answer-1", "chat-1"))
                .thenReturn(Optional.of(message("answer-1", "assistant", "Hi")));
        assertThat(service.get("request-1", actor).reply()).isEqualTo("Hi");
        verifyNoInteractions(llmServiceProvider);
    }

    @Test
    void firstRoutingCreatesAndLinksTheMessageWithoutExecutingIt() {
        AgentMessageRoute request = request("ROUTING");
        when(users.findById("user-1")).thenReturn(Optional.of(actor));
        when(llmServiceProvider.getIfAvailable()).thenReturn(llm);
        when(requests.lockOwned("request-1", "user-1", "ROUTING")).thenReturn(Optional.of(request));
        when(chatService.createSession("user-1", actor)).thenReturn(new ChatSessionView(
                "chat-1", "ACTIVE", OffsetDateTime.now(), List.of(), List.of()));
        when(chatService.addMessage("chat-1", "user", "Hello")).thenReturn(message("source-1", "user", "Hello"));
        when(requests.markRouted(eq("request-1"), eq("chat-1"), eq("CREATE"), eq("source-1"), any()))
                .thenReturn(1);

        service.route(request);

        verify(requests).markRouted("request-1", "chat-1", "CREATE", "source-1", new String[0]);
        verifyNoInteractions(llm);
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentSessionFollowsLastRoutedInputInsteadOfLatestAssistantActivity() throws Exception {
        AgentMessageRoute request = request("ROUTING");
        when(users.findById("user-1")).thenReturn(Optional.of(actor));
        when(llmServiceProvider.getIfAvailable()).thenReturn(llm);
        when(requests.findLastRoutedSession("user-1")).thenReturn(Optional.of("chat-current"));
        when(sessions.findByIdAndUserId("chat-current", "user-1"))
                .thenReturn(Optional.of(session("chat-current")));
        when(sessions.findRecentActiveByUserId("user-1", 8))
                .thenReturn(List.of(session("chat-late-answer"), session("chat-current")));
        when(messages.findRecentBySessionId(anyString(), eq(4)))
                .thenReturn(List.of(message("old", "user", "Earlier topic")));
        when(llm.runChatWithTools(anyList(), anyString(), anyList())).thenAnswer(invocation -> {
            List<LlmMessage> input = invocation.getArgument(0);
            assertThat(input.getFirst().content().indexOf("Chat session id: chat-current"))
                    .isLessThan(input.getFirst().content().indexOf("Chat session id: chat-late-answer"));
            List<LlmTool<?>> availableTools = invocation.getArgument(2);
            ((LlmTool<AgentMessageService.RouteToolParameters>) availableTools.getFirst()).handler()
                    .execute(new AgentMessageService.RouteToolParameters("CONTINUE", "chat-current"));
            return new LlmResult<>(null, "model", "", 1L, 1L, 2L);
        });
        when(requests.lockOwned("request-1", "user-1", "ROUTING")).thenReturn(Optional.of(request));
        when(chatService.getSessionForChat("chat-current", "user-1")).thenReturn(session("chat-current"));
        when(chatService.addMessage("chat-current", "user", "Hello"))
                .thenReturn(message("source-1", "user", "Hello"));
        when(requests.markRouted(eq("request-1"), eq("chat-current"), eq("CONTINUE"), eq("source-1"), any()))
                .thenReturn(1);
        service.route(request);
        verify(chatService, never()).createSession(anyString(), any());
    }

    @Test
    void expiredRoutingCannotCreateAChatOrMessage() {
        when(users.findById("user-1")).thenReturn(Optional.of(actor));
        when(llmServiceProvider.getIfAvailable()).thenReturn(llm);
        assertThatThrownBy(() -> service.route(request("ROUTING")))
                .isInstanceOf(IllegalStateException.class);
        verify(chatService, never()).createSession(anyString(), any());
        verify(chatService, never()).addMessage(anyString(), anyString(), anyString());
    }

    @Test
    void expiredExecutionCannotPersistAnAssistantReply() {
        AgentMessageRoute request = request("PROCESSING");
        request.setRoutedChatSessionId("chat-1");
        request.setSourceMessageId("source-1");
        when(users.findById("user-1")).thenReturn(Optional.of(actor));
        when(llmServiceProvider.getIfAvailable()).thenReturn(llm);
        when(chatService.getSessionForChat("chat-1", "user-1")).thenReturn(session("chat-1"));
        when(messages.findByIdAndSessionId("source-1", "chat-1"))
                .thenReturn(Optional.of(message("source-1", "user", "Hello")));
        when(findProjects.forContext(any())).thenReturn(new LlmTool<>("find_projects", "Find projects",
                FindProjectsTool.Parameters.class, ignored -> Map.of()));
        when(llm.runChatWithTools(anyList(), anyString(), anyList()))
                .thenReturn(new LlmResult<>(null, "model", "Hi", 1L, 1L, 2L));
        assertThatThrownBy(() -> service.execute(request)).isInstanceOf(IllegalStateException.class);
        verify(chatService, never()).addMessage(anyString(), anyString(), anyString());
    }

    @Test
    void executionUsesTheContextSnapshotAndPersistsTheReply() {
        AgentMessageRoute request = prepareExecution();
        request.setContextIds(new String[]{"context-old"});
        ChatSessionContext oldContext = context("context-old", "USER", "person-old");
        ChatSessionContext laterContext = context("context-later", "USER", "person-later");
        when(chatService.contextsForChat("chat-1", "user-1", actor)).thenReturn(List.of(oldContext, laterContext));
        when(chatService.listContexts("chat-1", "user-1", actor)).thenReturn(List.of(
                new ChatSessionContextView("context-old", "USER", "person-old", "Original person", null, null),
                new ChatSessionContextView("context-later", "USER", "person-later", "Later person", null, null)));
        when(llm.runChatWithTools(anyList(), anyString(), anyList())).thenAnswer(invocation -> {
            assertThat((String) invocation.getArgument(1)).contains("Original person").doesNotContain("Later person");
            return new LlmResult<>(null, "model", "Hi", 1L, 1L, 2L);
        });
        when(requests.lockOwned("request-1", "user-1", "PROCESSING")).thenReturn(Optional.of(request));
        when(chatService.addMessage("chat-1", "assistant", "Hi")).thenReturn(message("answer-1", "assistant", "Hi"));
        when(requests.markCompleted("request-1", "answer-1")).thenReturn(1);
        service.execute(request);
        verify(requests).markCompleted("request-1", "answer-1");
    }

    @Test
    void expiredExecutionCannotCreateProposalsThroughATool() {
        AgentMessageRoute request = prepareExecution();
        request.setContextIds(new String[]{"context-project"});
        when(chatService.contextsForChat("chat-1", "user-1", actor))
                .thenReturn(List.of(context("context-project", "PROJECT", "project-1")));
        Project project = new Project();
        project.setId("project-1");
        project.setName("Project");
        when(projects.findById("project-1")).thenReturn(Optional.of(project));
        when(projectAccess.hasProjectRole("project-1", "user-1", "EDITOR")).thenReturn(true);
        java.util.concurrent.atomic.AtomicBoolean wroteProposal = new java.util.concurrent.atomic.AtomicBoolean();
        when(proposeChanges.forMessage(any(), eq("project-1"), eq("chat-1"), eq("source-1"), eq("Hello")))
                .thenReturn(new LlmTool<>("propose_workspace_changes", "Propose changes",
                        WorkspaceChangeProposalService.ProposalDraft.class, draft -> {
                            wroteProposal.set(true);
                            return Map.of();
                        }));
        when(llm.runChatWithTools(anyList(), anyString(), anyList())).thenAnswer(invocation -> {
            List<LlmTool<?>> available = invocation.getArgument(2);
            available.stream().filter(tool -> tool.name().equals("propose_workspace_changes"))
                    .findFirst().orElseThrow().handler().execute(null);
            throw new AssertionError("Expired tool must be rejected");
        });
        assertThatThrownBy(() -> service.execute(request)).isInstanceOf(IllegalStateException.class);
        assertThat(wroteProposal).isFalse();
        verify(chatService, never()).addMessage(anyString(), anyString(), anyString());
    }

    private AgentMessageRoute prepareExecution() {
        AgentMessageRoute request = request("PROCESSING");
        request.setRoutedChatSessionId("chat-1");
        request.setSourceMessageId("source-1");
        when(users.findById("user-1")).thenReturn(Optional.of(actor));
        when(llmServiceProvider.getIfAvailable()).thenReturn(llm);
        when(chatService.getSessionForChat("chat-1", "user-1")).thenReturn(session("chat-1"));
        when(messages.findByIdAndSessionId("source-1", "chat-1"))
                .thenReturn(Optional.of(message("source-1", "user", "Hello")));
        when(findProjects.forContext(any())).thenReturn(new LlmTool<>("find_projects", "Find projects",
                FindProjectsTool.Parameters.class, ignored -> Map.of()));
        return request;
    }

    private ChatSessionContext context(String id, String type, String entityId) {
        ChatSessionContext context = new ChatSessionContext();
        context.setId(id);
        context.setEntityType(type);
        context.setEntityId(entityId);
        return context;
    }

    private AgentMessageRoute request(String status) {
        AgentMessageRoute request = new AgentMessageRoute();
        request.setId("request-1");
        request.setUserId("user-1");
        request.setMessage("Hello");
        request.setStatus(status);
        request.setIngestionSequence(1L);
        request.setContextIds(new String[0]);
        return request;
    }

    private ChatSession session(String id) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId("user-1");
        session.setStatus("ACTIVE");
        return session;
    }

    private ChatMessage message(String id, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setChatSessionId("chat-1");
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        return message;
    }
}
