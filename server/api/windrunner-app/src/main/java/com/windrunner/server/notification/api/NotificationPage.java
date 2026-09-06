package com.windrunner.server.notification.api;

import java.util.List;

public record NotificationPage(List<UserNotificationView> items, long unreadCount, long totalItems) {
}
