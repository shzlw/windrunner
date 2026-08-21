package com.windrunner.server.chat;

import com.windrunner.server.chat.api.ChatSessionView;
import com.windrunner.server.chat.domain.ChatMessage;
import com.windrunner.server.chat.domain.ChatSession;
import com.windrunner.server.chat.persistence.ChatMessageRepository;
import com.windrunner.server.chat.persistence.ChatSessionRepository;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
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
        return toView(getOrCreateActiveSession(projectId, userId));
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
    public ChatMessage addMessage(String chatSessionId, String role, String content) {
        if (chatSessionId == null || chatSessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat session id is required");
        }
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat message content is required");
        }
        String id = idGenerator.generate(EntityIdType.CHAT_MESSAGE);
        messageRepository.insert(id, chatSessionId, role, content);
        return messageRepository.findByIdAndSessionId(id, chatSessionId)
                .orElseThrow(() -> new IllegalStateException("Created chat message could not be loaded"));
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
