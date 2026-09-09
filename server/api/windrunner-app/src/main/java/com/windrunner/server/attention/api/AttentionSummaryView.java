package com.windrunner.server.attention.api;

import com.windrunner.server.notification.api.UserNotificationView;

import java.util.List;

public record AttentionSummaryView(
        List<AttentionWorkItemView> overdueAssignments,
        boolean hasMoreOverdueAssignments,
        List<AttentionWorkItemView> blockedAssignments,
        boolean hasMoreBlockedAssignments,
        List<AttentionWorkItemView> dueSoonWork,
        boolean hasMoreDueSoonWork,
        List<AttentionWorkItemView> newAssignments,
        boolean hasMoreNewAssignments,
        List<UserNotificationView> importantUnreadNotifications,
        boolean hasMoreImportantUnreadNotifications
) {
}
