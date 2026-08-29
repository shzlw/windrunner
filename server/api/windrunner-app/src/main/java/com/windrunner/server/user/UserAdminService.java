package com.windrunner.server.user;

import com.windrunner.server.audit.*;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.domain.ProjectMember;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.user.api.*;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final String DEFAULT_TIMEZONE = "UTC";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AppUserRepository appUserRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final AuditLogService auditLogService;
    private final EntityIdGenerator idGenerator;

    public UserPageResponse listUsers(int page, int size, AppUser currentUser) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        boolean superAdmin = AppRoles.isSuperAdmin(currentUser.getGlobalRole());
        long totalItems = superAdmin
                ? appUserRepository.countUsersWithUserOrAdminRole()
                : appUserRepository.countUsersWithUserRole();
        List<UserResponse> items = (superAdmin
                ? appUserRepository.findUserAndAdminPage(normalizedSize, (long) normalizedPage * normalizedSize)
                : appUserRepository.findUserPage(normalizedSize, (long) normalizedPage * normalizedSize))
                .stream()
                .map(this::toResponse)
                .toList();

        return UserPageResponse.builder()
                .items(items)
                .page(normalizedPage)
                .size(normalizedSize)
                .totalItems(totalItems)
                .totalPages((int) Math.ceil(totalItems / (double) normalizedSize))
                .build();
    }

    public UserResponse getUser(String id, AppUser currentUser) {
        return toResponse(requireManageableUser(id, currentUser));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest createRequest, AppUser currentUser) {
        validateCreateRequest(createRequest);

        String normalizedUsername = normalizeUsername(createRequest.getUsername());
        String normalizedEmail = normalizeEmail(createRequest.getEmail());
        String normalizedTimezone = normalizeTimezone(createRequest.getTimezone());
        String normalizedRole = normalizeCreatableRole(createRequest.getGlobalRole(), currentUser);
        ensureUniqueUser(normalizedUsername, normalizedEmail, null);

        AppUser user = new AppUser();
        user.setId(idGenerator.generate(EntityIdType.USER));
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setDisplayName(StringUtils.hasText(createRequest.getDisplayName()) ? createRequest.getDisplayName().trim() : null);
        user.setTitle(normalizeOptionalText(createRequest.getTitle()));
        user.setBio(normalizeOptionalText(createRequest.getBio()));
        user.setTimezone(normalizedTimezone);
        user.setPasswordHash(passwordEncoder.encode(createRequest.getPassword()));
        user.setStatus(normalizeStatus(createRequest.getStatus()));
        user.setGlobalRole(normalizedRole);
        user.setMustChangePassword(Boolean.TRUE);
        user.setCreatedAt(DateUtils.now());
        user.setUpdatedAt(user.getCreatedAt());
        int inserted = appUserRepository.insertUser(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTitle(),
                user.getBio(),
                user.getTimezone(),
                user.getPasswordHash(),
                user.getStatus(),
                user.getGlobalRole(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one app_user row to be inserted but got " + inserted);
        }
        AppUser savedUser = authService.findExistingUser(user.getId());
        auditLogService.logAfterCommit(new AuditLogEntry(
                currentUser.getId(),
                AuditActions.CREATE,
                AuditEntityTypes.USER,
                savedUser.getId(),
                null,
                AuditOutcomes.SUCCESS,
                "Created user " + savedUser.getUsername(),
                null,
                auditLogService.json(userSnapshot(savedUser)),
                null,
                null));
        return toResponse(savedUser);
    }

    @Transactional
    public UserResponse updateUser(String id, UpdateUserRequest updateRequest, AppUser currentUser) {
        if (updateRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        AppUser user = requireManageableUser(id, currentUser);
        Map<String, Object> before = userSnapshot(user);
        String normalizedUsername = normalizeUsername(updateRequest.getUsername());
        String normalizedEmail = normalizeEmail(updateRequest.getEmail());
        String normalizedTimezone = normalizeTimezone(updateRequest.getTimezone());
        String normalizedRole = normalizeUpdatableRole(updateRequest.getGlobalRole(), user.getGlobalRole(), currentUser);
        ensureUniqueUser(normalizedUsername, normalizedEmail, id);

        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setDisplayName(StringUtils.hasText(updateRequest.getDisplayName()) ? updateRequest.getDisplayName().trim() : null);
        user.setTitle(normalizeOptionalText(updateRequest.getTitle()));
        user.setBio(normalizeOptionalText(updateRequest.getBio()));
        user.setTimezone(normalizedTimezone);
        user.setStatus(normalizeStatus(updateRequest.getStatus()));
        user.setGlobalRole(normalizedRole);
        user.setUpdatedAt(DateUtils.now());
        int updated = appUserRepository.updateUserProfile(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTitle(),
                user.getBio(),
                user.getTimezone(),
                user.getStatus(),
                user.getGlobalRole(),
                user.getUpdatedAt()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one app_user row to be updated but got " + updated);
        }
        AppUser savedUser = authService.findExistingUser(user.getId());
        Map<String, Object> after = userSnapshot(savedUser);
        auditLogService.logAfterCommit(new AuditLogEntry(
                currentUser.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.USER,
                savedUser.getId(),
                null,
                AuditOutcomes.SUCCESS,
                "Updated user " + savedUser.getUsername(),
                auditLogService.json(before),
                auditLogService.json(after),
                auditLogService.changes(before, after),
                null));
        return toResponse(savedUser);
    }

    @Transactional
    public UserResponse updatePassword(String id, UpdateUserPasswordRequest updateRequest, AppUser currentUser) {
        if (updateRequest == null || !StringUtils.hasText(updateRequest.getNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is required");
        }

        AppUser user = requireManageableUser(id, currentUser);
        Map<String, Object> before = userSnapshot(user);
        user.setPasswordHash(passwordEncoder.encode(updateRequest.getNewPassword()));
        user.setMustChangePassword(updateRequest.getMustChangePassword() == null || updateRequest.getMustChangePassword());
        user.setUpdatedAt(DateUtils.now());
        int updated = appUserRepository.updateUserPassword(
                user.getId(),
                user.getPasswordHash(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getUpdatedAt()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one app_user row to be updated but got " + updated);
        }
        AppUser savedUser = authService.findExistingUser(user.getId());
        authService.revokeUserSessions(savedUser.getId());
        Map<String, Object> after = userSnapshot(savedUser);
        auditLogService.logAfterCommit(new AuditLogEntry(
                currentUser.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.USER,
                savedUser.getId(),
                null,
                AuditOutcomes.SUCCESS,
                "Reset password for user " + savedUser.getUsername(),
                auditLogService.json(before),
                auditLogService.json(after),
                auditLogService.changes(before, after),
                null));
        return toResponse(savedUser);
    }

    @Transactional
    public void deleteUser(String id, AppUser currentUser) {
        AppUser user = requireManageableUser(id, currentUser);
        Map<String, Object> before = userSnapshot(user);
        for (ProjectMember projectMember : projectMemberRepository.findByUserId(id)) {
            projectAccessService.requireAnotherOwnerBeforeRemovingOwner(
                    projectMember.getProjectId(),
                    ProjectRoles.OWNER.equals(projectMember.getRole())
            );
        }
        projectMemberRepository.deleteByUserId(id);
        teamMemberRepository.deleteByUserId(id);
        authService.revokeUserSessions(id);

        int updated = appUserRepository.updateUserStatus(id, UserStatuses.INACTIVE, DateUtils.now());
        if (updated != 1) {
            throw new IllegalStateException("Expected one app_user row to be updated but got " + updated);
        }
        auditLogService.logAfterCommit(new AuditLogEntry(
                currentUser.getId(),
                AuditActions.DELETE,
                AuditEntityTypes.USER,
                user.getId(),
                null,
                AuditOutcomes.SUCCESS,
                "Deleted user " + user.getUsername(),
                auditLogService.json(before),
                null,
                null,
                null));
    }

    private void validateCreateRequest(CreateUserRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (!StringUtils.hasText(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        PasswordPolicy.assertValid(request.getPassword());
    }

    private void ensureUniqueUser(String username, String email, String excludedUserId) {
        if (appUserRepository.findByUsername(username)
                .filter(existing -> !existing.getId().equals(excludedUserId))
                .isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (StringUtils.hasText(email) && appUserRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(excludedUserId))
                .isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be a valid email address");
        }
        return normalizedEmail;
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase() : UserStatuses.ACTIVE;
    }

    private String normalizeTimezone(String timezone) {
        String normalizedTimezone = StringUtils.hasText(timezone) ? timezone.trim() : DEFAULT_TIMEZONE;
        try {
            return ZoneId.of(normalizedTimezone).getId();
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timezone must be a valid IANA time zone");
        }
    }

    private String normalizeCreatableRole(String requestedRole, AppUser currentUser) {
        String normalizedRole = StringUtils.hasText(requestedRole)
                ? requestedRole.trim().toUpperCase()
                : AppRoles.USER;

        if (AppRoles.isUser(normalizedRole)) {
            return AppRoles.USER;
        }
        if (AppRoles.isAdmin(normalizedRole) && AppRoles.isSuperAdmin(currentUser.getGlobalRole())) {
            return AppRoles.ADMIN;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot create user with role " + normalizedRole);
    }

    private String normalizeUpdatableRole(String requestedRole, String currentRole, AppUser currentUser) {
        if (!StringUtils.hasText(requestedRole)) {
            return currentRole;
        }
        if (!AppRoles.isSuperAdmin(currentUser.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Superadmin access is required to update global role");
        }
        return normalizeCreatableRole(requestedRole, currentUser);
    }

    private AppUser requireManageableUser(String id, AppUser currentUser) {
        AppUser user = authService.findExistingUser(id);
        if (AppRoles.isSuperAdmin(user.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (AppRoles.isAdmin(user.getGlobalRole()) && !AppRoles.isSuperAdmin(currentUser.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private UserResponse toResponse(AppUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .title(user.getTitle())
                .bio(user.getBio())
                .timezone(user.getTimezone())
                .status(user.getStatus())
                .globalRole(user.getGlobalRole())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private Map<String, Object> userSnapshot(AppUser user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", user.getId());
        snapshot.put("username", user.getUsername());
        snapshot.put("email", user.getEmail());
        snapshot.put("displayName", user.getDisplayName());
        snapshot.put("title", user.getTitle());
        snapshot.put("bio", user.getBio());
        snapshot.put("timezone", user.getTimezone());
        snapshot.put("status", user.getStatus());
        snapshot.put("globalRole", user.getGlobalRole());
        snapshot.put("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()));
        snapshot.put("createdAt", user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
        snapshot.put("updatedAt", user.getUpdatedAt() == null ? null : user.getUpdatedAt().toString());
        return snapshot;
    }
}
