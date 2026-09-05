package com.windrunner.server.auth;

import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActorAuthorizationTest {
    @Mock AppUserRepository users;
    @InjectMocks AuthService auth;
    AppUser actor(String role, String status) {
        AppUser user = new AppUser(); user.setId("actor"); user.setGlobalRole(role); user.setStatus(status); return user;
    }
    @Test void staleAdminRoleDoesNotAuthorizeAdminTool() {
        AppUser stale = actor("ADMIN", "ACTIVE");
        when(users.findById("actor")).thenReturn(Optional.of(actor("USER", "ACTIVE")));
        assertThatThrownBy(() -> auth.requireAdminActor(stale)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
    @Test void inactiveActorIsRejectedEvenWithStaleActiveContext() {
        when(users.findById("actor")).thenReturn(Optional.of(actor("ADMIN", "INACTIVE")));
        assertThatThrownBy(() -> auth.requireActiveActor(actor("ADMIN", "ACTIVE"))).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
    @Test void deletedActorCannotUseTools() {
        assertThatThrownBy(() -> auth.requireActiveActor(actor("ADMIN", "ACTIVE"))).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
    @Test void currentActorIsLoadedFromDatabase() {
        AppUser fresh = actor("SUPERADMIN", "ACTIVE");
        when(users.findById("actor")).thenReturn(Optional.of(fresh));
        assertThat(auth.requireAdminActor(actor("USER", "ACTIVE"))).isSameAs(fresh);
    }
}
