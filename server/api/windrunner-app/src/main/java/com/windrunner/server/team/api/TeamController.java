package com.windrunner.server.team.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.domain.ProjectTeam;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamJoinRequest;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.user.api.UserResponse;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/teams")
public class TeamController {

    private final TeamService teamService;
    private final AuthService authService;
    private final AppUserRepository appUserRepository;

    @GetMapping
    public ApiResponse<List<Team>> listTeams(HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return ApiResponse.success(teamService.listTeams(actor.getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Team> getTeam(@PathVariable("id") String id, HttpServletRequest request) {
        authService.requireCurrentUser(request);
        return ApiResponse.success(teamService.getTeam(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Team> createTeam(@RequestBody CreateTeamRequest createRequest, HttpServletRequest request) {
        AppUser actor = authService.requireAdmin(request);
        return ApiResponse.success(teamService.createTeam(createRequest, actor));
    }

    @PutMapping("/{id}")
    public ApiResponse<Team> updateTeam(@PathVariable("id") String id,
                                        @RequestBody Team team,
                                        HttpServletRequest request) {
        AppUser actor = authService.requireAdmin(request);
        return ApiResponse.success(teamService.updateTeam(id, team, actor));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTeam(@PathVariable("id") String id, HttpServletRequest request) {
        AppUser actor = authService.requireAdmin(request);
        teamService.deleteTeam(id, actor);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<TeamMember>> listMembers(@PathVariable("id") String id, HttpServletRequest request) {
        authService.requireCurrentUser(request);
        return ApiResponse.success(teamService.listMembers(id));
    }

    @GetMapping("/{id}/assignable-users")
    public ApiResponse<List<UserResponse>> listAssignableUsers(@PathVariable("id") String id,
                                                                @RequestParam(name = "query", required = false) String query,
                                                                @RequestParam(name = "limit", required = false, defaultValue = "20") int limit,
                                                                HttpServletRequest request) {
        AppUser actor = authService.requireAdmin(request);
        teamService.getTeam(id);
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        return ApiResponse.success(appUserRepository.findAvailableUsersForTeam(id, normalizedQuery,
                        AppRoles.isSuperAdmin(actor.getGlobalRole()), normalizedLimit)
                .stream()
                .map(this::toUserResponse)
                .toList());
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TeamMember> addMember(@PathVariable("id") String id,
                                             @RequestBody TeamLinkRequest linkRequest,
                                             HttpServletRequest request) {
        AppUser actor = authService.requireAdmin(request);
        return ApiResponse.success(teamService.addMember(id, linkRequest, actor));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable("id") String id,
                                          @PathVariable("userId") String userId,
                                          HttpServletRequest request) {
        AppUser actor = authService.requireAdmin(request);
        teamService.removeMember(id, userId, actor);
        return ApiResponse.success();
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

    @GetMapping("/{id}/projects")
    public ApiResponse<List<ProjectTeam>> listProjects(@PathVariable("id") String id, HttpServletRequest request) {
        authService.requireCurrentUser(request);
        return ApiResponse.success(teamService.listProjects(id));
    }

    @PostMapping("/{id}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectTeam> addProject(@PathVariable("id") String id,
                                               @RequestBody TeamLinkRequest linkRequest,
                                               HttpServletRequest request) {
        AppUser actor = authService.requireAdmin(request);
        return ApiResponse.success(teamService.addProject(id, linkRequest, actor));
    }

    @DeleteMapping("/{id}/projects/{projectId}")
    public ApiResponse<Void> removeProject(@PathVariable("id") String id,
                                           @PathVariable("projectId") String projectId,
                                           HttpServletRequest request) {
        AppUser actor = authService.requireAdmin(request);
        teamService.removeProject(id, projectId, actor);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/join-requests")
    public ApiResponse<List<TeamJoinRequest>> listJoinRequests(@PathVariable("id") String id, HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return ApiResponse.success(teamService.listPendingJoinRequests(id, actor));
    }

    @PostMapping("/{id}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TeamJoinRequest> requestJoin(@PathVariable("id") String id, HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return ApiResponse.success(teamService.requestJoin(id, actor));
    }

    @PostMapping("/{id}/join-requests/{requestId}/decision")
    public ApiResponse<TeamJoinRequest> decideJoinRequest(@PathVariable("id") String id,
                                                          @PathVariable("requestId") String requestId,
                                                          @RequestBody TeamJoinDecisionRequest decisionRequest,
                                                          HttpServletRequest request) {
        AppUser actor = authService.requireCurrentUser(request);
        return ApiResponse.success(teamService.decideJoinRequest(id, requestId, decisionRequest, actor));
    }
}
