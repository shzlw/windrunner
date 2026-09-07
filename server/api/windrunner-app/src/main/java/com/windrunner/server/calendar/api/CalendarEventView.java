package com.windrunner.server.calendar.api;

import com.windrunner.server.calendar.domain.CalendarEvent;

import java.time.OffsetDateTime;

public record CalendarEventView(
        String id,
        String userId,
        String title,
        String description,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String timezone,
        boolean allDay,
        String workItemId,
        String createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CalendarEventView from(CalendarEvent event) {
        return new CalendarEventView(
                event.getId(),
                event.getUserId(),
                event.getTitle(),
                event.getDescription(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getTimezone(),
                Boolean.TRUE.equals(event.getAllDay()),
                event.getWorkItemId(),
                event.getCreatedByUserId(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }
}
