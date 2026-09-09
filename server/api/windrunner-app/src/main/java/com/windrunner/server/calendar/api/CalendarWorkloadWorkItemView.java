package com.windrunner.server.calendar.api;

import java.time.LocalDate;

public record CalendarWorkloadWorkItemView(
        String workItemId,
        String projectId,
        String projectName,
        String title,
        String status,
        LocalDate startedOn,
        LocalDate dueDate
) {
    public static CalendarWorkloadWorkItemView from(CalendarWorkItemView item) {
        return new CalendarWorkloadWorkItemView(
                item.workItemId(), item.projectId(), item.projectName(), item.title(), item.status(),
                item.startedOn(), item.dueDate());
    }
}
