package com.windrunner.server.chat;

import com.windrunner.server.agent.persistence.AgentMessageRequestRepository;
import com.windrunner.server.chat.api.*;
import com.windrunner.server.chat.domain.ChatMessage;
import com.windrunner.server.chat.domain.ChatSession;
import com.windrunner.server.chat.domain.ChatSessionContext;
import com.windrunner.server.chat.persistence.ChatMessageRepository;
import com.windrunner.server.chat.persistence.ChatSessionContextRepository;
import com.windrunner.server.chat.persistence.ChatSessionRepository;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.UserStatuses;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.persistence.WorkItemRepository;
import com.windrunner.server.work.persistence.WorkspaceChangeProposalRepository;
import com.windrunner.server.work.persistence.WorkspaceChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ChatService {
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_CONTEXTS_PER_SESSION = 50;

    private final ChatSessionRepository sessionRepository;
    private final ChatSessionContextRepository contextRepository;
    private final ChatMessageRepository messageRepository;
    private final AgentMessageRequestRepository agentMessageRequestRepository;
    private final WorkspaceChangeProposalRepository proposalRepository;
    private final WorkspaceChangeRepository changeRepository;
    private final EntityIdGenerator idGenerator;
    private final ProjectRepository projects;
    private final ProjectAccessService projectAccessService;
    private final TeamRepository teams;
    private final AppUserRepository users;
    private final WorkItemRepository workItems;

    @Transactional
    public ChatSessionView createSession(String userId, AppUser actor) {
        ChatSession latestSession = sessionRepository.findLatestActiveByUserId(userId).orElse(null);
        if (latestSession != null && messageRepository.findBySessionIdOrdered(latestSession.getId()).isEmpty()) {
            return getSession(latestSession.getId(), userId, actor);
        }

        String id = idGenerator.generate(EntityIdType.CHAT_SESSION);
        sessionRepository.insert(id, userId);
        return getSession(id, userId, actor);
    }

    public ChatSession getSessionForChat(String sessionId, String userId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat session id is required");
        }
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
    }

    public ChatSessionView getSession(String sessionId, String userId, AppUser actor) {
        ChatSession session = requireSession(sessionId, userId);
        return toView(session, actor);
    }

    @Transactional
    public ChatSessionPageView listSessions(String userId, String requestedQuery, int requestedLimit, int requestedOffset) {
        int limit = Math.min(Math.max(requestedLimit, 1), 50);
        int offset = Math.max(requestedOffset, 0);
        String query = requestedQuery == null ? null : requestedQuery.trim();
        List<ChatSession> sessions = sessionRepository.findPageByUserId(userId, query, limit + 1, offset);
        boolean hasMore = sessions.size() > limit;
        List<ChatSession> page = sessions.stream().limit(limit).toList();
        List<String> sessionIds = page.stream().map(ChatSession::getId).toList();
        Map<String, ChatMessage> firstUserMessages = sessionIds.isEmpty() ? Map.of()
                : messageRepository.findFirstUserMessages(sessionIds).stream()
                .collect(Collectors.toMap(ChatMessage::getChatSessionId, Function.identity()));
        return new ChatSessionPageView(page.stream().map(s -> toSummary(s, firstUserMessages.get(s.getId()))).toList(), hasMore, offset, limit);
    }

    @Transactional
    public void renameSession(String sessionId, String userId, String requestedTitle) {
        if (requestedTitle == null || requestedTitle.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat session title is required");
        }
        String title = requestedTitle.replaceAll("\\s+", " ").trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat session title must be 120 characters or fewer");
        }
        if (sessionRepository.updateTitle(sessionId, userId, title) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found");
        }
    }

    @Transactional
    public void deleteSession(String sessionId, String userId) {
        requireSession(sessionId, userId);
        contextRepository.deleteBySessionId(sessionId);
        changeRepository.deleteByChatSessionId(sessionId);
        proposalRepository.deleteByChatSessionId(sessionId);
        agentMessageRequestRepository.deleteByChatSessionId(sessionId);
        messageRepository.deleteBySessionId(sessionId);
        if (sessionRepository.deleteSession(sessionId, userId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found");
        }
    }

    public List<ChatSessionContextView> listContexts(String sessionId, String userId, AppUser actor) {
        requireSession(sessionId, userId);
        return contextRepository.findBySessionId(sessionId).stream()
                .filter(context -> isAccessible(context, actor))
                .map(context -> toContextView(context, actor)).toList();
    }

    @Transactional
    public ChatSessionContextView addContext(String sessionId, String userId, AppUser actor,
                                             String requestedType, String requestedEntityId) {
        requireSession(sessionId, userId);
        String entityType = normalizeEntityType(requestedType);
        String entityId = requestedEntityId == null ? "" : requestedEntityId.trim();
        if (entityId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Context entity id is required");
        ContextDescriptor descriptor = resolveContext(entityType, entityId, actor);
        if (contextRepository.findBySessionId(sessionId).size() >= MAX_CONTEXTS_PER_SESSION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A maximum of 50 context items can be added");
        }
        String id = idGenerator.generate(EntityIdType.CHAT_SESSION_CONTEXT);
        contextRepository.insert(id, sessionId, entityType, entityId);
        ChatSessionContext context = contextRepository.findByIdAndSessionId(id, sessionId).orElseGet(() -> contextRepository.findBySessionId(sessionId).stream()
                .filter(item -> entityType.equals(item.getEntityType()) && entityId.equals(item.getEntityId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Created context could not be loaded")));
        return new ChatSessionContextView(context.getId(), entityType, entityId, descriptor.label(), descriptor.projectId(), context.getCreatedAt());
    }

    @Transactional
    public void deleteContext(String sessionId, String contextId, String userId) {
        requireSession(sessionId, userId);
        if (contextRepository.delete(contextId, sessionId) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session context not found");
    }

    public List<ChatSessionContext> contextsForChat(String sessionId, String userId, AppUser actor) {
        requireSession(sessionId, userId);
        return contextRepository.findBySessionId(sessionId).stream().filter(context -> isAccessible(context, actor)).toList();
    }

    @Transactional
    public ChatMessage addMessage(String chatSessionId, String role, String content) {
        if (chatSessionId == null || chatSessionId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat session id is required");
        if (content == null || content.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat message content is required");
        String id = idGenerator.generate(EntityIdType.CHAT_MESSAGE);
        messageRepository.insert(id, chatSessionId, role, content);
        if ("user".equalsIgnoreCase(role)) {
            sessionRepository.setTitleFromFirstMessage(chatSessionId, id, titleFromFirstMessage(content));
        }
        sessionRepository.touch(chatSessionId);
        return messageRepository.findByIdAndSessionId(id, chatSessionId).orElseThrow(() -> new IllegalStateException("Created chat message could not be loaded"));
    }

    private ChatSession requireSession(String sessionId, String userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
    }

    private String normalizeEntityType(String requestedType) {
        String value = requestedType == null ? "" : requestedType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PROJECT", "TEAM", "USER", "WORK_ITEM").contains(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported chat context type");
        return value;
    }

    private ContextDescriptor resolveContext(String entityType, String entityId, AppUser actor) {
        switch (entityType) {
            case "PROJECT" -> {
                projectAccessService.requireProjectRole(entityId, actor, ProjectRoles.VIEWER);
                Project project = projects.findById(entityId).orElseThrow(() -> notFound("Project"));
                return new ContextDescriptor(project.getName(), project.getId());
            }
            case "TEAM" -> {
                Team team = teams.findById(entityId).orElseThrow(() -> notFound("Team"));
                return new ContextDescriptor(team.getName(), null);
            }
            case "USER" -> {
                AppUser user = users.findById(entityId).orElseThrow(() -> notFound("User"));
                if (!UserStatuses.ACTIVE.equalsIgnoreCase(user.getStatus())) throw notFound("User");
                return new ContextDescriptor(user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getUsername() : user.getDisplayName(), null);
            }
            case "WORK_ITEM" -> {
                WorkItem item = workItems.findById(entityId).orElseThrow(() -> notFound("Work item"));
                projectAccessService.requireProjectRole(item.getProjectId(), actor, ProjectRoles.VIEWER);
                return new ContextDescriptor(item.getTitle(), item.getProjectId());
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported chat context type");
        }
    }

    private ChatSessionContextView toContextView(ChatSessionContext context, AppUser actor) {
        ContextDescriptor d = resolveContext(context.getEntityType(), context.getEntityId(), actor);
        return new ChatSessionContextView(context.getId(), context.getEntityType(), context.getEntityId(), d.label(), d.projectId(), context.getCreatedAt());
    }

    private boolean isAccessible(ChatSessionContext context, AppUser actor) {
        try {
            resolveContext(context.getEntityType(), context.getEntityId(), actor);
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private ResponseStatusException notFound(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " not found"); }

    private ChatSessionSummaryView toSummary(ChatSession session, ChatMessage firstUserMessage) {
        String title = session.getTitle();
        if (title == null || title.isBlank()) title = firstUserMessage == null || firstUserMessage.getContent() == null ? "New conversation" : firstUserMessage.getContent().replaceAll("\\s+", " ").trim();
        title = title.replaceAll("\\s+", " ").trim();
        if (title.length() > MAX_TITLE_LENGTH) title = title.substring(0, 117) + "...";
        return new ChatSessionSummaryView(session.getId(), session.getStatus(), session.getCreatedAt(), session.getUpdatedAt(), title.isBlank() ? "New conversation" : title);
    }

    private String titleFromFirstMessage(String content) {
        String title = content.replaceAll("\\s+", " ").trim();
        if (title.length() > MAX_TITLE_LENGTH) return title.substring(0, 117) + "...";
        return title;
    }

    private ChatSessionView toView(ChatSession session, AppUser actor) {
        return new ChatSessionView(session.getId(), session.getStatus(), session.getCreatedAt(), List.copyOf(messageRepository.findBySessionIdOrdered(session.getId())), listContexts(session.getId(), session.getUserId(), actor));
    }

    private record ContextDescriptor(String label, String projectId) { }
}
