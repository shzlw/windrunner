package com.windrunner.server.notification.api;

import com.windrunner.server.notification.domain.UserNotification;

import java.time.OffsetDateTime;

public record UserNotificationView(
        String id,
        String notificationType,
        String actorUserId,
        String projectId,
        String workItemId,
        String title,
        String message,
        boolean read,
        OffsetDateTime createdAt
) {
    public static UserNotificationView from(UserNotification notification) {
        return new UserNotificationView(
                notification.getId(),
                notification.getNotificationType(),
                notification.getActorUserId(),
                notification.getProjectId(),
                notification.getWorkItemId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReadAt() != null,
                notification.getCreatedAt());
    }
}
