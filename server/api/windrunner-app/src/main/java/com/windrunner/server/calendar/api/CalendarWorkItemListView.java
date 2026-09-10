package com.windrunner.server.calendar.api;

import java.util.List;

public record CalendarWorkItemListView(
        List<CalendarWorkItemView> items,
        boolean truncated
) {
}
