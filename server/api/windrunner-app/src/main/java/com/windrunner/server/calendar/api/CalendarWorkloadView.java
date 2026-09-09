package com.windrunner.server.calendar.api;

import java.time.LocalDate;
import java.util.List;

public record CalendarWorkloadView(
        String teamId,
        String teamName,
        LocalDate from,
        LocalDate to,
        List<CalendarWorkloadMemberView> members,
        boolean hasMoreMembers,
        boolean workItemsTruncated,
        int teamAssignedWorkItemCount,
        List<CalendarWorkloadWorkItemView> teamAssignedWorkItems
) {
}
