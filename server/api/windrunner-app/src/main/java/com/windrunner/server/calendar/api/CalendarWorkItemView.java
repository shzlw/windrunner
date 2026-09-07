package com.windrunner.server.calendar.api;

import java.time.LocalDate;

public record CalendarWorkItemView(
        String workItemId,
        String projectId,
        String projectName,
        String title,
        String status,
        String assigneeType,
        String assigneeId,
        LocalDate startedOn,
        LocalDate dueDate,
        boolean overdue
) {
}
