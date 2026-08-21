package com.windrunner.server.notification.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.notification.NotificationDeliveryService;
import com.windrunner.server.notification.NotificationService;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/notifications")
public class NotificationController {

    private final AuthService authService;
    private final NotificationService notificationService;
    private final NotificationDeliveryService deliveryService;

    @GetMapping
    public ApiResponse<NotificationPageView> list(
            @RequestParam(name = "unread", defaultValue = "false") boolean unread,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "offset", defaultValue = "0") long offset,
            HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        NotificationService.NotificationPage page = notificationService.listForUser(actor.getId(), unread, limit, offset);
        return ApiResponse.success(new NotificationPageView(page.items(), page.unreadCount(), page.totalItems()));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return deliveryService.connect(actor.getId());
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable("id") String notificationId, HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        notificationService.markRead(actor.getId(), notificationId);
        return ApiResponse.success();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        notificationService.markAllRead(actor.getId());
        return ApiResponse.success();
    }

    public record NotificationPageView(List<UserNotificationView> items, long unreadCount, long totalItems) {
    }
}
