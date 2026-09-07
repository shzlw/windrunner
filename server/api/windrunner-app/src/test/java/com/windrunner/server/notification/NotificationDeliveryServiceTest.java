package com.windrunner.server.notification;

import com.windrunner.server.notification.domain.UserNotification;
import com.windrunner.server.notification.persistence.UserNotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationDeliveryServiceTest {
    @Test
    void advancesPastFullPageOfIdenticalTimestamps() {
        UserNotificationRepository repository = mock(UserNotificationRepository.class);
        NotificationDeliveryService service = new NotificationDeliveryService(repository);
        service.connect("user");
        OffsetDateTime timestamp = OffsetDateTime.now().plusSeconds(1);
        List<UserNotification> firstPage = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> notification(String.format("%03d", i), timestamp)).toList();
        when(repository.findCreatedAfter(eq("user"), any(), eq(""), eq(100))).thenReturn(firstPage);
        when(repository.findCreatedAfter("user", timestamp, "100", 100))
                .thenReturn(List.of(notification("101", timestamp)));
        service.pollNotifications();
        service.pollNotifications();
        service.pollNotifications();
        verify(repository).findCreatedAfter("user", timestamp, "100", 100);
        verify(repository).findCreatedAfter("user", timestamp, "101", 100);
    }

    @Test
    void simultaneousDisconnectAndConnectKeepsNewConnectionRegistered() throws Exception {
        NotificationDeliveryService service = new NotificationDeliveryService(mock(UserNotificationRepository.class));
        try (var executor = Executors.newFixedThreadPool(2)) {
            SseEmitter current = service.connect("user");
            for (int i = 0; i < 100; i++) {
                SseEmitter previous = current;
                CyclicBarrier barrier = new CyclicBarrier(2);
                var removal = executor.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    ReflectionTestUtils.invokeMethod(service, "remove", "user", previous);
                    return null;
                });
                var connection = executor.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return service.connect("user");
                });
                removal.get(5, TimeUnit.SECONDS);
                current = connection.get(5, TimeUnit.SECONDS);
                Map<?, ?> channels = (Map<?, ?>) ReflectionTestUtils.getField(service, "channels");
                Object channel = channels.get("user");
                assertThat(channel).isNotNull();
                List<?> emitters = (List<?>) ReflectionTestUtils.getField(channel, "emitters");
                assertThat(emitters.size()).isEqualTo(1);
                assertThat(emitters.getFirst()).isSameAs(current);
            }
        }
    }

    private UserNotification notification(String id, OffsetDateTime timestamp) {
        UserNotification notification = new UserNotification();
        notification.setId(id);
        notification.setRecipientUserId("user");
        notification.setCreatedAt(timestamp);
        notification.setTitle("Assigned");
        return notification;
    }
}
