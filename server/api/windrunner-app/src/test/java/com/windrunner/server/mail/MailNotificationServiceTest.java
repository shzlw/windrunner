package com.windrunner.server.mail;

import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.mail.persistence.MailNotificationRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailNotificationServiceTest {
    private final MailNotificationRepository queue = mock(MailNotificationRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final MailProperties properties = new MailProperties();
    private final MailNotificationService service =
            new MailNotificationService(properties, queue, users, new EntityIdGenerator());

    @Test
    void disabledMailDoesNotQueue() {
        service.queueAssignments(List.of("user"), "actor", "item");
        service.queueAccountNotice("user", "user@example.com", "Password changed");
        verifyNoInteractions(queue, users);
    }

    @Test
    void assignmentsDeduplicateAndExcludeActorAndInactiveUsers() {
        properties.setEnabled(true);
        AppUser active = user("ACTIVE");
        when(users.findById("user")).thenReturn(Optional.of(active));
        when(users.findById("inactive")).thenReturn(Optional.of(user("INACTIVE")));
        service.queueAssignments(List.of("user", "user", "actor", "inactive"), "actor", "item");
        verify(queue).insert(anyString(), eq("user"), eq("new@example.com"), eq("item"),
                eq("actor"), isNull(), eq(120));
        verifyNoMoreInteractions(queue);
        verify(users, never()).findById("actor");
    }

    @Test
    void ordinaryProfileEditsDoNotSendMail() {
        properties.setEnabled(true);
        service.queueAccountChanges(Map.of("email", "new@example.com", "globalRole", "USER",
                "status", "ACTIVE", "displayName", "Old name"), user("ACTIVE"));
        verifyNoInteractions(queue);
    }

    @Test
    void addressChangesNotifyPreviousAddressImmediately() {
        properties.setEnabled(true);
        service.queueAccountChanges(Map.of("email", "old@example.com", "globalRole", "USER",
                "status", "ACTIVE"), user("ACTIVE"));
        verify(queue).insert(anyString(), eq("user"), eq("old@example.com"), isNull(), isNull(),
                contains("email address was changed"), eq(0));
    }

    private AppUser user(String status) {
        AppUser user = new AppUser();
        user.setId("user");
        user.setEmail("new@example.com");
        user.setStatus(status);
        user.setGlobalRole("USER");
        return user;
    }
}
