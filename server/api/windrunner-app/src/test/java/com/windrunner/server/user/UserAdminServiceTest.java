package com.windrunner.server.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.windrunner.server.audit.AuditLogService;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.user.api.UpdateUserRequest;
import com.windrunner.server.user.api.UserResponse;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AuthService authService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private AuditLogService auditLogService;

    private UserAdminService userAdminService;

    @BeforeEach
    void setUp() {
        userAdminService = new UserAdminService(
                appUserRepository,
                authService,
                passwordEncoder,
                teamMemberRepository,
                projectMemberRepository,
                projectAccessService,
                auditLogService,
                new EntityIdGenerator());
    }

    @Test
    void superadminCanUpdateGlobalRole() {
        AppUser target = user("user-1", AppRoles.USER);
        AppUser actor = user("admin-1", AppRoles.SUPERADMIN);
        when(authService.findExistingUser(target.getId())).thenReturn(target);
        when(appUserRepository.findByUsername("updated-user")).thenReturn(Optional.empty());
        when(appUserRepository.findByEmail("updated@example.com")).thenReturn(Optional.empty());
        when(appUserRepository.updateUserProfile(
                eq(target.getId()),
                eq("updated-user"),
                eq("updated@example.com"),
                eq("Updated User"),
                any(),
                any(),
                eq("UTC"),
                eq(UserStatuses.ACTIVE),
                eq(AppRoles.ADMIN),
                any()))
                .thenReturn(1);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername("updated-user");
        request.setEmail("updated@example.com");
        request.setDisplayName("Updated User");
        request.setTimezone("UTC");
        request.setStatus(UserStatuses.ACTIVE);
        request.setGlobalRole(AppRoles.ADMIN);

        UserResponse response = userAdminService.updateUser(target.getId(), request, actor);

        assertThat(response.globalRole()).isEqualTo(AppRoles.ADMIN);
        verify(appUserRepository).updateUserProfile(
                eq(target.getId()),
                eq("updated-user"),
                eq("updated@example.com"),
                eq("Updated User"),
                any(),
                any(),
                eq("UTC"),
                eq(UserStatuses.ACTIVE),
                eq(AppRoles.ADMIN),
                any());
    }

    @Test
    void nonSuperadminCannotUpdateGlobalRole() {
        AppUser target = user("user-1", AppRoles.USER);
        AppUser actor = user("admin-1", AppRoles.ADMIN);
        when(authService.findExistingUser(target.getId())).thenReturn(target);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername(target.getUsername());
        request.setTimezone("UTC");
        request.setGlobalRole(AppRoles.ADMIN);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userAdminService.updateUser(target.getId(), request, actor));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(appUserRepository);
    }

    private AppUser user(String id, String globalRole) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(id);
        user.setGlobalRole(globalRole);
        return user;
    }
}
