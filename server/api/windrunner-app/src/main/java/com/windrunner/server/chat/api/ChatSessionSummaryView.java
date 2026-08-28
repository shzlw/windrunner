package com.windrunner.server.chat.api;

import java.time.OffsetDateTime;

public record ChatSessionSummaryView(
        String id,
        String projectId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String title
) {
}
