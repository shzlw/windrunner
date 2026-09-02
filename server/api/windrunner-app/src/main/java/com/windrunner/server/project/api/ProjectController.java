package com.windrunner.server.project.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.audit.*;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectContentDeletionService;
import com.windrunner.server.project.ProjectRoles;
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
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TeamRepository teamRepository;
    private final AppUserRepository appUserRepository;
    private final ProjectAccessService projectAccessService;
    private final AuditLogService auditLogService;
    private final AuthService authService;
    private final EntityIdGenerator idGenerator;
    private final ProjectContentDeletionService projectContentDeletionService;

    @GetMapping
    public ApiResponse<List<Project>> listProjects(HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        List<Project> projects = AppRoles.isSuperAdmin(actor.getGlobalRole())
                ? projectRepository.findAllByOrderByNameAscIdAsc()
                : projectRepository.findVisibleToUser(actor.getId());
        populateOwnerUserIds(projects);
        return ApiResponse.success(projects);
    }

    @GetMapping("/{id}")
    public ApiResponse<Project> getProject(@PathVariable("id") String id, HttpServletRequest request) {
        projectAccessService.requireProjectRole(id, authService.requireCurrentUser(request), ProjectRoles.VIEWER);
        return ApiResponse.success(projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")));
    }

    private void populateOwnerUserIds(List<Project> projects) {
        if (projects.isEmpty()) {
            return;
        }
        Map<String, List<String>> ownerUserIdsByProjectId = new LinkedHashMap<>();
        projectMemberRepository.findOwnersByProjectIds(projects.stream().map(Project::getId).toList())
                .forEach(member -> ownerUserIdsByProjectId
                        .computeIfAbsent(member.getProjectId(), ignored -> new java.util.ArrayList<>())
                        .add(member.getUserId()));
        Map<String, String> ownerDisplayNamesById = new LinkedHashMap<>();
        appUserRepository.findAllById(ownerUserIdsByProjectId.values().stream().flatMap(List::stream).distinct().toList())
                .forEach(user -> ownerDisplayNamesById.put(user.getId(), displayUser(user)));
        projects.forEach(project -> {
            List<String> ownerUserIds = ownerUserIdsByProjectId.getOrDefault(project.getId(), List.of());
            project.setOwnerUserIds(ownerUserIds);
            Map<String, String> ownerDisplayNames = new LinkedHashMap<>();
            ownerUserIds.forEach(ownerUserId -> ownerDisplayNames.put(ownerUserId, ownerDisplayNamesById.getOrDefault(ownerUserId, "Unknown user")));
            project.setOwnerDisplayNames(ownerDisplayNames);
        });
    }

    private String displayUser(AppUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        return user.getUsername();
    }

    private void requireAssignableProjectMember(String userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (AppRoles.isSuperAdmin(user.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Super admin users cannot be added as project members");
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ApiResponse<Project> createProject(@RequestBody CreateProjectRequest createRequest, HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        if (createRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String name = requireText(createRequest.name(), "Project name is required");
        List<String> ownerUserIds = normalizeIds(createRequest.ownerUserIds(), "Owner user id is required");
        List<String> ownerTeamIds = normalizeIds(createRequest.ownerTeamIds(), "Owner team id is required");
        if (ownerUserIds.isEmpty() && ownerTeamIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one project owner is required");
        }
        for (String ownerUserId : ownerUserIds) {
            requireAssignableProjectMember(ownerUserId);
        }
        for (String ownerTeamId : ownerTeamIds) {
            if (!teamRepository.existsById(ownerTeamId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project owner team not found");
            }
        }

        Project project = new Project();
        project.setId(idGenerator.generate(EntityIdType.PROJECT));
        project.setName(name);
        project.setCreatedByUserId(actor.getId());
        projectRepository.insert(project.getId(), project.getName(), actor.getId());
        // A creator must always be able to open and administer the project they just created.
        if (!AppRoles.isSuperAdmin(actor.getGlobalRole())) {
            projectMemberRepository.upsert(project.getId(), actor.getId(), ProjectRoles.OWNER);
        }
        for (String ownerUserId : ownerUserIds) {
            projectMemberRepository.upsert(project.getId(), ownerUserId, ProjectRoles.OWNER);
        }
        for (String ownerTeamId : ownerTeamIds) {
            projectTeamRepository.upsert(project.getId(), ownerTeamId, ProjectRoles.OWNER);
        }
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor.getId(),
                AuditActions.CREATE,
                AuditEntityTypes.PROJECT,
                project.getId(),
                project.getId(),
                AuditOutcomes.SUCCESS,
                "Created project " + project.getName(),
                null,
                auditLogService.json(projectSnapshot(project)),
                null,
                null));
        return ApiResponse.success(project);
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Project> updateProject(@PathVariable("id") String id,
                                              @RequestBody Project project,
                                              HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(id, actor, ProjectRoles.OWNER);
        Project beforeProject = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        Map<String, Object> before = projectSnapshot(beforeProject);
        validateName(project);
        project.setId(id);
        if (projectRepository.update(project.getId(), project.getName()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        Map<String, Object> after = projectSnapshot(project);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.PROJECT,
                project.getId(),
                project.getId(),
                AuditOutcomes.SUCCESS,
                "Updated project " + project.getName(),
                auditLogService.json(before),
                auditLogService.json(after),
                auditLogService.changes(before, after),
                null));
        return ApiResponse.success(project);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> deleteProject(@PathVariable("id") String id, HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(id, actor, ProjectRoles.OWNER);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        Map<String, Object> before = projectSnapshot(project);
        projectContentDeletionService.deleteProjectContent(id);
        projectTeamRepository.deleteByProjectId(id);
        projectMemberRepository.deleteByProjectId(id);
        projectRepository.deleteById(id);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor.getId(),
                AuditActions.DELETE,
                AuditEntityTypes.PROJECT,
                project.getId(),
                project.getId(),
                AuditOutcomes.SUCCESS,
                "Deleted project " + project.getName(),
                auditLogService.json(before),
                null,
                null,
                null));
        return ApiResponse.success();
    }

    @GetMapping("/{id}/teams")
    public ApiResponse<List<ProjectTeamRepository.ProjectTeamWithName>> listProjectTeams(@PathVariable("id") String id, HttpServletRequest request) {
        projectAccessService.requireProjectRole(id, authService.requireCurrentUser(request), ProjectRoles.VIEWER);
        requireProject(id);
        return ApiResponse.success(projectTeamRepository.findByProjectIdWithTeamName(id));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<ProjectMember>> listProjectMembers(@PathVariable("id") String id, HttpServletRequest request) {
        projectAccessService.requireProjectRole(id, authService.requireCurrentUser(request), ProjectRoles.VIEWER);
        requireProject(id);
        return ApiResponse.success(projectMemberRepository.findByProjectId(id));
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ApiResponse<ProjectMember> upsertProjectMember(@PathVariable("id") String id,
                                                          @RequestBody TeamLinkRequest linkRequest,
                                                          HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
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
        return ApiResponse.success(projectMember);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Transactional
    public ApiResponse<Void> removeProjectMember(@PathVariable("id") String id,
                                                 @PathVariable("userId") String userId,
                                                 HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(id, actor, ProjectRoles.OWNER);
        Project project = requireProject(id);
        ProjectMember existing = projectMemberRepository.findByProjectIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project member not found"));
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(id, ProjectRoles.OWNER.equals(existing.getRole()));
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
        return ApiResponse.success();
    }

    @PostMapping("/{id}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ApiResponse<ProjectTeam> assignTeam(@PathVariable("id") String id,
                                               @RequestBody TeamLinkRequest linkRequest,
                                               HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
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
        return ApiResponse.success(projectTeam);
    }

    @DeleteMapping("/{id}/teams/{teamId}")
    @Transactional
    public ApiResponse<Void> unassignTeam(@PathVariable("id") String id,
                                          @PathVariable("teamId") String teamId,
                                          HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(id, actor, ProjectRoles.OWNER);
        Project project = requireProject(id);
        ProjectTeam existing = projectTeamRepository.findByProjectIdAndTeamId(id, teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project team not found"));
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(id, ProjectRoles.OWNER.equals(existing.getRole()));
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
        return ApiResponse.success();
    }

    private void validateName(Project project) {
        if (project.getName() == null || project.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name is required");
        }
        project.setName(project.getName().trim());
    }

    private Map<String, Object> projectSnapshot(Project project) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", project.getId());
        snapshot.put("name", project.getName());
        snapshot.put("createdByUserId", project.getCreatedByUserId());
        snapshot.put("archivedAt", project.getArchivedAt() == null ? null : project.getArchivedAt().toString());
        return snapshot;
    }

    private Project requireProject(String id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private List<String> normalizeIds(List<String> values, String blankMessage) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, blankMessage);
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private String normalizeProjectRole(String role) {
        try {
            return ProjectRoles.normalize(role);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
