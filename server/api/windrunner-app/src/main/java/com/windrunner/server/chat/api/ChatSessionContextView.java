package com.windrunner.server.chat.api;

import java.time.OffsetDateTime;

public record ChatSessionContextView(
        String id,
        String entityType,
        String entityId,
        String label,
        String projectId,
        OffsetDateTime createdAt
) {
}
