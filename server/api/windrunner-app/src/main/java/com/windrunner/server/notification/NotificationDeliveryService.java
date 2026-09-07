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
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ignored -> remove(userId, emitter));
        channels.compute(userId, (id, current) -> {
            UserChannel channel = current == null ? new UserChannel() : current;
            channel.emitters.add(emitter);
            return channel;
        });
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
            synchronized (channel) {
                for (UserNotification notification : notifications.findCreatedAfter(
                        userId, channel.cursor, channel.cursorId, 100)) {
                    channel.emitters.forEach(emitter -> send(userId, emitter, notification));
                    channel.cursor = notification.getCreatedAt();
                    channel.cursorId = notification.getId();
                }
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
        channels.computeIfPresent(userId, (id, channel) -> {
            channel.emitters.remove(emitter);
            return channel.emitters.isEmpty() ? null : channel;
        });
    }

    private static final class UserChannel {
        private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        private OffsetDateTime cursor = OffsetDateTime.now();
        private String cursorId = "";
    }
}
