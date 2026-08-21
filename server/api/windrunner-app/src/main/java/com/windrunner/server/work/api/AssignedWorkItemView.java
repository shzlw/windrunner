package com.windrunner.server.work.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AssignedWorkItemView(
        String projectId,
        String projectName,
        String workItemId,
        String title,
        String type,
        String status,
        LocalDate dueDate,
        String priority,
        OffsetDateTime updatedAt
) {
}
