package com.windrunner.server.notification;

import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.notification.api.UserNotificationView;
import com.windrunner.server.notification.domain.UserNotification;
import com.windrunner.server.notification.persistence.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String WORK_ITEM_ASSIGNED = "WORK_ITEM_ASSIGNED";
    private static final String WORK_ITEM_ACTIVITY = "WORK_ITEM_ACTIVITY";

    private final UserNotificationRepository notifications;
    private final com.windrunner.server.user.persistence.AppUserRepository users;
    private final EntityIdGenerator ids;

    @Transactional
    public void notifyWorkItemAssigned(Collection<String> recipientUserIds,
                                       String actorUserId,
                                       String projectId,
                                       String workItemId,
                                       String workItemTitle) {
        if (recipientUserIds == null) {
            return;
        }
        String title = "Work item assigned";
        String message = "You were assigned to “" + workItemTitle + "”.";
        recipientUserIds.stream().filter(userId -> userId != null && !userId.isBlank()).distinct().forEach(userId ->
                notifications.insert(
                        ids.generate(EntityIdType.USER_NOTIFICATION),
                        userId,
                        WORK_ITEM_ASSIGNED,
                        actorUserId,
                        projectId,
                        workItemId,
                        title,
                        message));
    }

    @Transactional
    public void notifyWorkItemActivity(Collection<String> recipientUserIds,
                                       String actorUserId,
                                       String projectId,
                                       String workItemId,
                                       String title,
                                       List<String> changeSummaries) {
        if (recipientUserIds == null || recipientUserIds.isEmpty() || changeSummaries == null || changeSummaries.isEmpty()) {
            return;
        }
        String message = actorLabel(actorUserId) + " " + String.join(", ", changeSummaries) + " on “" + title + "”.";
        recipientUserIds.stream().filter(userId -> userId != null && !userId.isBlank()).distinct().forEach(userId ->
                notifications.insert(
                        ids.generate(EntityIdType.USER_NOTIFICATION),
                        userId,
                        WORK_ITEM_ACTIVITY,
                        actorUserId,
                        projectId,
                        workItemId,
                        "Work item updated",
                        message));
    }

    private String actorLabel(String actorUserId) {
        return users.findById(actorUserId)
                .map(user -> {
                    if (user.getDisplayName() != null && !user.getDisplayName().isBlank())
                        return user.getDisplayName().trim();
                    if (user.getUsername() != null && !user.getUsername().isBlank()) return user.getUsername();
                    return user.getEmail();
                })
                .orElse("Someone");
    }

    public NotificationPage listForUser(String userId, boolean unreadOnly, int limit, long offset) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        long safeOffset = Math.max(0, offset);
        List<UserNotification> rows = unreadOnly
                ? notifications.findUnreadForUser(userId, safeLimit)
                : notifications.findForUser(userId, safeLimit, safeOffset);
        return new NotificationPage(
                rows.stream().map(UserNotificationView::from).toList(),
                notifications.countUnreadForUser(userId),
                notifications.countForUser(userId));
    }

    public void markRead(String userId, String notificationId) {
        notifications.markRead(notificationId, userId);
    }

    public void markAllRead(String userId) {
        notifications.markAllRead(userId);
    }

    public record NotificationPage(List<UserNotificationView> items, long unreadCount, long totalItems) {
    }
}
