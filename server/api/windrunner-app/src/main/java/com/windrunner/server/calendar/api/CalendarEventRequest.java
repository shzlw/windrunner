package com.windrunner.server.calendar.api;

import java.time.OffsetDateTime;

public record CalendarEventRequest(
        String userId,
        String eventType,
        String title,
        String description,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String timezone,
        Boolean allDay,
        Boolean showAsBusy,
        String workItemId
) {
}
