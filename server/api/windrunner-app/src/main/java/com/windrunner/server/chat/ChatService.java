package com.windrunner.server.chat;

import com.windrunner.server.chat.api.ChatSessionView;
import com.windrunner.server.chat.api.ChatSessionSummaryView;
import com.windrunner.server.chat.domain.ChatMessage;
import com.windrunner.server.chat.domain.ChatSession;
import com.windrunner.server.chat.persistence.ChatMessageRepository;
import com.windrunner.server.chat.persistence.ChatSessionRepository;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.work.persistence.WorkspaceChangeProposalRepository;
import com.windrunner.server.work.persistence.WorkspaceChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final WorkspaceChangeProposalRepository proposalRepository;
    private final WorkspaceChangeRepository changeRepository;
    private final EntityIdGenerator idGenerator;

    @Transactional
    public ChatSession getOrCreateActiveSession(String projectId, String userId) {
        return sessionRepository.findActive(projectId, userId).orElseGet(() -> {
            String id = idGenerator.generate(EntityIdType.CHAT_SESSION);
            sessionRepository.insert(id, projectId, userId);
            return sessionRepository.findByIdAndProjectId(id, projectId)
                    .orElseThrow(() -> new IllegalStateException("Created chat session could not be loaded"));
        });
    }

    public ChatSessionView getActiveSession(String projectId, String userId) {
        return sessionRepository.findActive(projectId, userId)
                .map(this::toView)
                .orElse(null);
    }

    public ChatSessionView getSession(String projectId, String sessionId, String userId) {
        ChatSession session = sessionRepository.findByIdAndProjectIdAndUserId(sessionId, projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
        return toView(session);
    }

    public ChatSession getSessionForChat(String projectId, String sessionId, String userId) {
        if (sessionId == null || sessionId.isBlank()) {
            return getOrCreateActiveSession(projectId, userId);
        }
        return sessionRepository.findByIdAndProjectIdAndUserId(sessionId, projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
    }

    @Transactional
    public List<ChatSessionSummaryView> listSessions(String projectId, String userId) {
        List<ChatSession> sessions = sessionRepository.findAllByProjectIdAndUserIdOrdered(projectId, userId);
        List<String> sessionIds = sessions.stream().map(ChatSession::getId).toList();
        Map<String, ChatMessage> firstUserMessages = sessionIds.isEmpty()
                ? Map.of()
                : messageRepository.findFirstUserMessages(sessionIds).stream()
                .collect(Collectors.toMap(ChatMessage::getChatSessionId, Function.identity()));
        return sessions.stream()
                .map(session -> toSummary(session, firstUserMessages.get(session.getId())))
                .toList();
    }

    @Transactional
    public ChatSessionView startNewSession(String projectId, String userId) {
        sessionRepository.archiveActive(projectId, userId);
        String id = idGenerator.generate(EntityIdType.CHAT_SESSION);
        sessionRepository.insert(id, projectId, userId);
        ChatSession session = sessionRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new IllegalStateException("Created chat session could not be loaded"));
        return toView(session);
    }

    @Transactional
    public void renameSession(String projectId, String sessionId, String userId, String requestedTitle) {
        if (requestedTitle == null || requestedTitle.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat session title is required");
        }
        String title = requestedTitle.replaceAll("\\s+", " ").trim();
        if (title.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat session title must be 120 characters or fewer");
        }
        if (sessionRepository.updateTitle(sessionId, projectId, userId, title) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found");
        }
    }

    @Transactional
    public void deleteSession(String projectId, String sessionId, String userId) {
        sessionRepository.findByIdAndProjectIdAndUserId(sessionId, projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found"));
        changeRepository.deleteByChatSessionId(sessionId);
        proposalRepository.deleteByChatSessionId(sessionId);
        messageRepository.deleteBySessionId(sessionId);
        if (sessionRepository.deleteSession(sessionId, projectId, userId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found");
        }
    }

    @Transactional
    public ChatMessage addMessage(String chatSessionId, String role, String content) {
        if (chatSessionId == null || chatSessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat session id is required");
        }
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat message content is required");
        }
        String id = idGenerator.generate(EntityIdType.CHAT_MESSAGE);
        messageRepository.insert(id, chatSessionId, role, content);
        sessionRepository.touch(chatSessionId);
        return messageRepository.findByIdAndSessionId(id, chatSessionId)
                .orElseThrow(() -> new IllegalStateException("Created chat message could not be loaded"));
    }

    private ChatSessionSummaryView toSummary(ChatSession session, ChatMessage firstUserMessage) {
        String title = session.getTitle();
        if (title == null || title.isBlank()) {
            title = firstUserMessage == null || firstUserMessage.getContent() == null
                    ? "New conversation"
                    : firstUserMessage.getContent().replaceAll("\\s+", " ").trim();
        }
        title = title.replaceAll("\\s+", " ").trim();
        if (title.length() > 120) {
            title = title.substring(0, 117) + "...";
        }
        return new ChatSessionSummaryView(
                session.getId(),
                session.getProjectId(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                title.isBlank() ? "New conversation" : title
        );
    }

    private ChatSessionView toView(ChatSession session) {
        List<ChatMessage> messages = messageRepository.findBySessionIdOrdered(session.getId());
        return new ChatSessionView(
                session.getId(),
                session.getProjectId(),
                session.getStatus(),
                session.getCreatedAt(),
                List.copyOf(messages)
        );
    }
}
