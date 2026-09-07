package com.windrunner.server.calendar.api;

import java.util.List;

public record CalendarScopeView(
        String teamId,
        String teamName,
        List<CalendarScopeMemberView> members
) {
}
