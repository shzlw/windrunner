package com.windrunner.server.attention.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AttentionWorkItemView(
        String projectId,
        String projectName,
        String workItemId,
        String title,
        String type,
        String status,
        LocalDate dueDate,
        String priority,
        OffsetDateTime assignedAt,
        boolean blocked
) {
}
