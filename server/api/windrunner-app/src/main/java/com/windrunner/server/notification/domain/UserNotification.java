package com.windrunner.server.notification.domain;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("user_notification")
public class UserNotification {

    @Id
    private String id;

    @Column("recipient_user_id")
    private String recipientUserId;

    @Column("notification_type")
    private String notificationType;

    @Column("actor_user_id")
    private String actorUserId;

    @Column("project_id")
    private String projectId;

    @Column("work_item_id")
    private String workItemId;

    private String title;
    private String message;

    @Column("read_at")
    private OffsetDateTime readAt;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
