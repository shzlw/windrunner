package com.windrunner.server.team.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.domain.ProjectTeam;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamJoinRequest;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal-api/v1/teams")
public class TeamController {

    private final TeamService teamService;
    private final AuthService authService;

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
