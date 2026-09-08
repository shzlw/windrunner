package com.windrunner.server.project.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectMembershipService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.ProjectService;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.domain.ProjectMember;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.api.TeamLinkRequest;
import com.windrunner.server.team.domain.ProjectTeam;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.api.UserResponse;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final AuthService authService;
    private final ProjectMembershipService memberships;
    private final ProjectService projectService;

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
                .forEach(user -> ownerDisplayNamesById.put(user.getId(), getDisplayName(user)));
        projects.forEach(project -> {
            List<String> ownerUserIds = ownerUserIdsByProjectId.getOrDefault(project.getId(), List.of());
            project.setOwnerUserIds(ownerUserIds);
            Map<String, String> ownerDisplayNames = new LinkedHashMap<>();
            ownerUserIds.forEach(ownerUserId -> ownerDisplayNames.put(ownerUserId, ownerDisplayNamesById.getOrDefault(ownerUserId, "Unknown user")));
            project.setOwnerDisplayNames(ownerDisplayNames);
        });
    }

    private String getDisplayName(AppUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        return user.getUsername();
    }

    private UserResponse toUserResponse(AppUser user) {
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

    private void requireAssignableProjectMember(String userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (AppRoles.isSuperAdmin(user.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Super admin users cannot be added as project members");
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Project> createProject(@RequestBody CreateProjectRequest createRequest, HttpServletRequest request) {
        return ApiResponse.success(projectService.createProject(createRequest, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Project> updateProject(@PathVariable("id") String id,
                                               @RequestBody Project project,
                                               HttpServletRequest request) {
        return ApiResponse.success(projectService.updateProject(id, project, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProject(@PathVariable("id") String id, HttpServletRequest request) {
        projectService.deleteProject(id, request);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/teams")
    public ApiResponse<List<ProjectTeamRepository.ProjectTeamWithName>> listProjectTeams(@PathVariable("id") String id, HttpServletRequest request) {
        projectAccessService.requireProjectRole(id, authService.requireCurrentUser(request), ProjectRoles.VIEWER);
        requireProject(id);
        return ApiResponse.success(projectTeamRepository.findByProjectIdWithTeamName(id));
    }

    @GetMapping("/{id}/assignable-teams")
    public ApiResponse<List<Team>> listAssignableTeams(@PathVariable("id") String id,
                                                        @RequestParam(name = "query", required = false) String query,
                                                        @RequestParam(name = "limit", required = false, defaultValue = "20") int limit,
                                                        HttpServletRequest request) {
        projectAccessService.requireProjectRole(id, authService.requireCurrentUser(request), ProjectRoles.OWNER);
        requireProject(id);
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        return ApiResponse.success(teamRepository.findAvailableTeamsForProject(id, normalizedQuery, normalizedLimit));
    }

    @GetMapping("/{id}/assignable-users")
    public ApiResponse<List<UserResponse>> listAssignableUsers(@PathVariable("id") String id,
                                                                @RequestParam(name = "query", required = false) String query,
                                                                @RequestParam(name = "limit", required = false, defaultValue = "20") int limit,
                                                                HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        projectAccessService.requireProjectRole(id, actor, ProjectRoles.OWNER);
        requireProject(id);
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        return ApiResponse.success(appUserRepository.findAvailableUsersForProject(id, normalizedQuery,
                        AppRoles.isSuperAdmin(actor.getGlobalRole()), normalizedLimit)
                .stream()
                .map(this::toUserResponse)
                .toList());
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<ProjectMember>> listProjectMembers(@PathVariable("id") String id, HttpServletRequest request) {
        projectAccessService.requireProjectRole(id, authService.requireCurrentUser(request), ProjectRoles.VIEWER);
        requireProject(id);
        return ApiResponse.success(projectMemberRepository.findByProjectId(id));
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectMember> upsertProjectMember(@PathVariable("id") String id,
                                                           @RequestBody TeamLinkRequest linkRequest,
                                                           HttpServletRequest request) {
        return ApiResponse.success(memberships.upsertProjectMember(id, linkRequest, authService.requireCurrentUser(request)));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeProjectMember(@PathVariable("id") String id,
                                                  @PathVariable("userId") String userId,
                                                  HttpServletRequest request) {
        memberships.removeProjectMember(id, userId, authService.requireCurrentUser(request));
        return ApiResponse.success();
    }

    @PostMapping("/{id}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectTeam> assignTeam(@PathVariable("id") String id,
                                                @RequestBody TeamLinkRequest linkRequest,
                                                HttpServletRequest request) {
        return ApiResponse.success(memberships.assignTeam(id, linkRequest, authService.requireCurrentUser(request)));
    }

    @DeleteMapping("/{id}/teams/{teamId}")
    public ApiResponse<Void> unassignTeam(@PathVariable("id") String id,
                                           @PathVariable("teamId") String teamId,
                                           HttpServletRequest request) {
        memberships.unassignTeam(id, teamId, authService.requireCurrentUser(request));
        return ApiResponse.success();
    }

    private Project requireProject(String id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }


}
