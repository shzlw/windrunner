package com.windrunner.server.notification;

import com.windrunner.server.notification.api.UserNotificationView;
import com.windrunner.server.notification.domain.UserNotification;
import com.windrunner.server.notification.persistence.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService implements SmartLifecycle {

    private final UserNotificationRepository notifications;
    private final Map<String, UserChannel> channels = new ConcurrentHashMap<>();
    private volatile boolean running;

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        closeEmitters();
        running = false;
    }

    @Override
    public void stop(Runnable callback) {
        closeEmitters();
        running = false;
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Stop before Spring Boot's webServerGracefulShutdown lifecycle phase.
        return Integer.MAX_VALUE;
    }

    public SseEmitter connect(String userId) {
        SseEmitter emitter = new SseEmitter(30 * 60_000L);
        UserChannel channel = channels.computeIfAbsent(userId, ignored -> new UserChannel());
        channel.emitters.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ignored -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("connected", true)));
        } catch (Exception exception) {
            remove(userId, emitter);
        }
        return emitter;
    }

    @Scheduled(fixedRate = 15_000L)
    public void sendHeartbeats() {
        channels.forEach((userId, channel) -> channel.emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (Exception exception) {
                remove(userId, emitter);
            }
        }));
    }

    @Scheduled(fixedRate = 30_000L)
    public void pollNotifications() {
        channels.forEach((userId, channel) -> {
            OffsetDateTime after = channel.cursor.minusSeconds(1);
            for (UserNotification notification : notifications.findCreatedAfter(userId, after, 100)) {
                if (channel.sentIds.add(notification.getId())) {
                    channel.emitters.forEach(emitter -> send(userId, emitter, notification));
                }
                if (notification.getCreatedAt() != null && notification.getCreatedAt().isAfter(channel.cursor)) {
                    channel.cursor = notification.getCreatedAt();
                }
            }
            if (channel.sentIds.size() > 500) {
                channel.sentIds.clear();
            }
        });
    }

    private void send(String userId, SseEmitter emitter, UserNotification notification) {
        try {
            emitter.send(SseEmitter.event()
                    .id(notification.getId())
                    .name("notification")
                    .data(UserNotificationView.from(notification)));
        } catch (Exception exception) {
            remove(userId, emitter);
        }
    }

    private void closeEmitters() {
        channels.values().forEach(channel -> channel.emitters.forEach(emitter -> {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // The client may already have disconnected while shutdown is closing the stream.
            }
        }));
        channels.clear();
    }

    private void remove(String userId, SseEmitter emitter) {
        UserChannel channel = channels.get(userId);
        if (channel == null) {
            return;
        }
        channel.emitters.remove(emitter);
        if (channel.emitters.isEmpty()) {
            channels.remove(userId, channel);
        }
    }

    private static final class UserChannel {
        private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        private final java.util.Set<String> sentIds = ConcurrentHashMap.newKeySet();
        private volatile OffsetDateTime cursor = OffsetDateTime.now();
    }
}
