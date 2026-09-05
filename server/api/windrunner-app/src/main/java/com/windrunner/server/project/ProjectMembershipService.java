package com.windrunner.server.project;

import com.windrunner.server.audit.*;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.domain.ProjectMember;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.api.TeamLinkRequest;
import com.windrunner.server.team.domain.ProjectTeam;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

@RequiredArgsConstructor
@org.springframework.stereotype.Service
public class ProjectMembershipService {
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TeamRepository teamRepository;
    private final AppUserRepository appUserRepository;
    private final ProjectAccessService projectAccessService;
    private final AuditLogService auditLogService;
    private final AuthService authService;
    @Transactional
    public ProjectMember upsertProjectMember(String id, TeamLinkRequest linkRequest, AppUser actor) {
        actor = authService.requireActiveActor(actor);
        projectAccessService.requireProjectRole(id, actor, ProjectRoles.OWNER);
        Project project = requireProject(id);
        String userId = requireText(linkRequest == null ? null : linkRequest.userId(), "User id is required");
        requireAssignableProjectMember(userId);
        String role = normalizeProjectRole(linkRequest == null ? null : linkRequest.role());
        ProjectMember existing = projectMemberRepository.findByProjectIdAndUserId(id, userId).orElse(null);
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(
                id,
                existing != null && ProjectRoles.OWNER.equals(existing.getRole()) && !ProjectRoles.OWNER.equals(role)
        );
        projectRepository.updateRevision(id);
        projectMemberRepository.upsert(id, userId, role);

        ProjectMember projectMember = new ProjectMember();
        projectMember.setProjectId(id);
        projectMember.setUserId(userId);
        projectMember.setRole(role);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.PROJECT,
                id,
                id,
                AuditOutcomes.SUCCESS,
                "Updated project member for " + project.getName(),
                null,
                null,
                null,
                auditLogService.json(Map.of("operation", "UPSERT_MEMBER", "userId", userId, "role", role))));
        return projectMember;
    }

    @Transactional
    public void removeProjectMember(String id, String userId, AppUser actor) {
        actor = authService.requireActiveActor(actor);
        projectAccessService.requireProjectRole(id, actor, ProjectRoles.OWNER);
        Project project = requireProject(id);
        ProjectMember existing = projectMemberRepository.findByProjectIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project member not found"));
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(id, ProjectRoles.OWNER.equals(existing.getRole()));
        projectRepository.updateRevision(id);
        projectMemberRepository.delete(id, userId);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.PROJECT,
                id,
                id,
                AuditOutcomes.SUCCESS,
                "Removed project member from " + project.getName(),
                null,
                null,
                null,
                auditLogService.json(Map.of("operation", "REMOVE_MEMBER", "userId", userId))));
        
    }

    @Transactional
    public ProjectTeam assignTeam(String id, TeamLinkRequest linkRequest, AppUser actor) {
        actor = authService.requireActiveActor(actor);
        projectAccessService.requireProjectRole(id, actor, ProjectRoles.OWNER);
        Project project = requireProject(id);
        String teamId = requireText(linkRequest == null ? null : linkRequest.teamId(), "Team id is required");
        if (!teamRepository.existsById(teamId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
        }
        String role = normalizeProjectRole(linkRequest == null ? null : linkRequest.role());
        ProjectTeam existing = projectTeamRepository.findByProjectIdAndTeamId(id, teamId).orElse(null);
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(
                id,
                existing != null && ProjectRoles.OWNER.equals(existing.getRole()) && !ProjectRoles.OWNER.equals(role)
        );
        projectRepository.updateRevision(id);
        projectTeamRepository.upsert(id, teamId, role);

        ProjectTeam projectTeam = new ProjectTeam();
        projectTeam.setProjectId(id);
        projectTeam.setTeamId(teamId);
        projectTeam.setRole(role);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.PROJECT,
                id,
                id,
                AuditOutcomes.SUCCESS,
                "Assigned team to project " + project.getName(),
                null,
                null,
                null,
                auditLogService.json(Map.of("operation", "ASSIGN_TEAM", "teamId", teamId, "role", role))));
        return projectTeam;
    }

    @Transactional
    public void unassignTeam(String id, String teamId, AppUser actor) {
        actor = authService.requireActiveActor(actor);
        projectAccessService.requireProjectRole(id, actor, ProjectRoles.OWNER);
        Project project = requireProject(id);
        ProjectTeam existing = projectTeamRepository.findByProjectIdAndTeamId(id, teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project team not found"));
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(id, ProjectRoles.OWNER.equals(existing.getRole()));
        projectRepository.updateRevision(id);
        projectTeamRepository.delete(id, teamId);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.PROJECT,
                id,
                id,
                AuditOutcomes.SUCCESS,
                "Unassigned team from project " + project.getName(),
                null,
                null,
                null,
                auditLogService.json(Map.of("operation", "UNASSIGN_TEAM", "teamId", teamId))));
        
    }

    @Transactional
    public void applyUserOptimistic(String projectId, String userId, String role, String action,
                                    OffsetDateTime expectedProjectUpdatedAt,
                                    OffsetDateTime expectedMembershipUpdatedAt,
                                    AppUser actor) {
        actor = authService.requireActiveActor(actor);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.OWNER);
        Project project = requireProject(projectId);
        requireAssignableProjectMember(userId);
        ProjectMember existing = projectMemberRepository.findByProjectIdAndUserId(projectId, userId).orElse(null);
        if (expectedProjectUpdatedAt == null || project.getUpdatedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This project has no usable concurrency revision. Ask AI to review it again.");
        }
        String normalizedRole = "REMOVE".equals(action) ? null : normalizeProjectRole(role);
        validateOptimisticMembership(action, existing, expectedMembershipUpdatedAt);
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(
                projectId,
                existing != null && ProjectRoles.OWNER.equals(existing.getRole()) && !ProjectRoles.OWNER.equals(normalizedRole));
        if (projectRepository.updateRevisionIfUnchanged(projectId, expectedProjectUpdatedAt) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This project changed after the proposal was created. Ask AI to review it again.");
        }
        int changed;
        if ("ADD".equals(action)) {
            changed = projectMemberRepository.insertIfAbsent(projectId, userId, normalizedRole);
        } else if ("UPDATE".equals(action)) {
            changed = projectMemberRepository.updateRoleIfUnchanged(projectId, userId, normalizedRole, expectedMembershipUpdatedAt);
        } else if ("REMOVE".equals(action)) {
            changed = projectMemberRepository.deleteIfUnchanged(projectId, userId, expectedMembershipUpdatedAt);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported membership action");
        }
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project membership changed after the proposal was created. Ask AI to review it again.");
        }
        auditLogService.logAfterCommit(new AuditLogEntry(actor.getId(), AuditActions.UPDATE, AuditEntityTypes.PROJECT,
                projectId, projectId, AuditOutcomes.SUCCESS, "Updated project member for " + project.getName(), null, null, null,
                auditLogService.json(Map.of("operation", action + "_MEMBER", "userId", userId,
                        "role", Objects.toString(normalizedRole, "")))));
    }

    @Transactional
    public void applyTeamOptimistic(String projectId, String teamId, String role, String action,
                                    OffsetDateTime expectedProjectUpdatedAt,
                                    OffsetDateTime expectedMembershipUpdatedAt,
                                    AppUser actor) {
        actor = authService.requireActiveActor(actor);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.OWNER);
        Project project = requireProject(projectId);
        if (!teamRepository.existsById(teamId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
        }
        ProjectTeam existing = projectTeamRepository.findByProjectIdAndTeamId(projectId, teamId).orElse(null);
        if (expectedProjectUpdatedAt == null || project.getUpdatedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This project has no usable concurrency revision. Ask AI to review it again.");
        }
        String normalizedRole = "REMOVE".equals(action) ? null : normalizeProjectRole(role);
        validateOptimisticMembership(action, existing, expectedMembershipUpdatedAt);
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(
                projectId,
                existing != null && ProjectRoles.OWNER.equals(existing.getRole()) && !ProjectRoles.OWNER.equals(normalizedRole));
        if (projectRepository.updateRevisionIfUnchanged(projectId, expectedProjectUpdatedAt) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This project changed after the proposal was created. Ask AI to review it again.");
        }
        int changed;
        if ("ADD".equals(action)) {
            changed = projectTeamRepository.insertIfAbsent(projectId, teamId, normalizedRole);
        } else if ("UPDATE".equals(action)) {
            changed = projectTeamRepository.updateRoleIfUnchanged(projectId, teamId, normalizedRole, expectedMembershipUpdatedAt);
        } else if ("REMOVE".equals(action)) {
            changed = projectTeamRepository.deleteIfUnchanged(projectId, teamId, expectedMembershipUpdatedAt);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported membership action");
        }
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project team membership changed after the proposal was created. Ask AI to review it again.");
        }
        auditLogService.logAfterCommit(new AuditLogEntry(actor.getId(), AuditActions.UPDATE, AuditEntityTypes.PROJECT,
                projectId, projectId, AuditOutcomes.SUCCESS, "Updated project team for " + project.getName(), null, null, null,
                auditLogService.json(Map.of("operation", action + "_TEAM", "teamId", teamId,
                        "role", Objects.toString(normalizedRole, "")))));
    }

    private void validateOptimisticMembership(String action, Object existing, OffsetDateTime expectedMembershipUpdatedAt) {
        if ("ADD".equals(action) && existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership changed after the proposal was created. Ask AI to review it again.");
        }
        if (!"ADD".equals(action) && existing == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership changed after the proposal was created. Ask AI to review it again.");
        }
        if (!"ADD".equals(action) && expectedMembershipUpdatedAt == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership has no usable concurrency revision. Ask AI to review it again.");
        }
    }
    private Project requireProject(String id) {
        return projectRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }
    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return value.trim();
    }
    private String normalizeProjectRole(String role) {
        try { return ProjectRoles.normalize(role); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }
    private void requireAssignableProjectMember(String id) {
        AppUser user = appUserRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (AppRoles.isSuperAdmin(user.getGlobalRole())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Super admin users cannot be added as project members");
    }
}
