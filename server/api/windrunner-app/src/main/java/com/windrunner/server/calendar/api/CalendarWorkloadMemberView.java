package com.windrunner.server.calendar.api;

import java.util.List;

public record CalendarWorkloadMemberView(
        String userId,
        String displayName,
        int scheduledWorkItemCount,
        List<CalendarWorkloadWorkItemView> scheduledWorkItems,
        int calendarEventCount,
        long calendarEventMinutes,
        int conflictCount,
        int unplannedWorkItemCount,
        List<CalendarWorkloadWorkItemView> unplannedWorkItems
) {
}
