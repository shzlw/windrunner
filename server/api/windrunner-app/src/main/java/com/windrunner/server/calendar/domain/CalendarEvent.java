package com.windrunner.server.calendar.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("calendar_event")
public class CalendarEvent {

    @Id
    private String id;

    @Column("user_id")
    private String userId;

    private String title;
    private String description;

    @Column("starts_at")
    private OffsetDateTime startsAt;

    @Column("ends_at")
    private OffsetDateTime endsAt;

    private String timezone;

    @Column("all_day")
    private Boolean allDay;

    @Column("work_item_id")
    private String workItemId;

    @Column("created_by_user_id")
    private String createdByUserId;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
