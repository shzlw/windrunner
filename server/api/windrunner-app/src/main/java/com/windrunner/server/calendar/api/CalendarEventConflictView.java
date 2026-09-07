package com.windrunner.server.calendar.api;

import com.windrunner.server.calendar.domain.CalendarEvent;

import java.time.OffsetDateTime;

public record CalendarEventConflictView(
        String id,
        String userId,
        String eventType,
        String title,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String timezone,
        boolean allDay,
        String workItemId
) {
    public static CalendarEventConflictView from(CalendarEvent event) {
        return new CalendarEventConflictView(
                event.getId(),
                event.getUserId(),
                event.getEventType(),
                event.getTitle(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getTimezone(),
                Boolean.TRUE.equals(event.getAllDay()),
                event.getWorkItemId());
    }
}
