package com.windrunner.server.chat.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatSessionView(
        String id,
        String status,
        OffsetDateTime createdAt,
        List<ChatSessionContextView> contexts
) {
}
