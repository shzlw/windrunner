package com.windrunner.server.chat.api;

import com.windrunner.server.chat.domain.ChatMessage;
import java.time.OffsetDateTime;
import java.util.List;

public record ChatSessionView(
        String id,
        String projectId,
        String status,
        OffsetDateTime createdAt,
        List<ChatMessage> messages
) {
}
