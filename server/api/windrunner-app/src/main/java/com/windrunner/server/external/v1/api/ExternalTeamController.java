package com.windrunner.server.external.v1.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.external.v1.dto.ExternalProjectTeamResponse;
import com.windrunner.server.external.v1.dto.ExternalTeamMemberResponse;
import com.windrunner.server.external.v1.dto.ExternalTeamResponse;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.api.CreateTeamRequest;
import com.windrunner.server.team.api.TeamLinkRequest;
import com.windrunner.server.team.domain.ProjectTeam;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamMember;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "Create teams, manage members and linked projects.")
public class ExternalTeamController {

    private final TeamService teamService;
    private final com.windrunner.server.team.persistence.TeamRepository teamRepository;
    private final ExternalAccessService externalAccessService;

    @GetMapping
    public ApiResponse<List<ExternalTeamResponse>> listTeams(@RequestParam(name = "page", defaultValue = "0") int page,
                                                             @RequestParam(name = "size", defaultValue = "50") int size,
                                                             HttpServletRequest request) {
        externalAccessService.requireScope(request, ApiKeyScopes.TEAMS_READ);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        List<Team> teams = teamRepository.findAllPage(normalizedSize, (long) normalizedPage * normalizedSize);
        long totalItems = teamRepository.countTeams();
        return ApiResponse.page(
                teams.stream().map(ExternalTeamResponse::from).toList(),
                normalizedPage,
                normalizedSize,
                totalItems,
                (int) Math.ceil(totalItems / (double) normalizedSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExternalTeamResponse> getTeam(@PathVariable("id") String id, HttpServletRequest request) {
        externalAccessService.requireScope(request, ApiKeyScopes.TEAMS_READ);
        return ApiResponse.success(ExternalTeamResponse.from(teamService.getTeam(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExternalTeamResponse> createTeam(@RequestBody CreateTeamRequest createRequest, HttpServletRequest request) {
        return ApiResponse.success(ExternalTeamResponse.from(teamService.createTeam(
                createRequest,
                externalAccessService.requireAdminScope(request, ApiKeyScopes.TEAMS_WRITE))));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExternalTeamResponse> updateTeam(@PathVariable("id") String id,
                                        @RequestBody Team team,
                                        HttpServletRequest request) {
        return ApiResponse.success(ExternalTeamResponse.from(teamService.updateTeam(
                id,
                team,
                externalAccessService.requireAdminScope(request, ApiKeyScopes.TEAMS_WRITE))));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTeam(@PathVariable("id") String id, HttpServletRequest request) {
        teamService.deleteTeam(id, externalAccessService.requireAdminScope(request, ApiKeyScopes.TEAMS_WRITE));
        return ApiResponse.success();
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<ExternalTeamMemberResponse>> listMembers(@PathVariable("id") String id, HttpServletRequest request) {
        externalAccessService.requireScope(request, ApiKeyScopes.TEAM_MEMBERS_READ);
        return ApiResponse.success(teamService.listMembers(id).stream()
                .map(ExternalTeamMemberResponse::from)
                .toList());
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExternalTeamMemberResponse> addMember(@PathVariable("id") String id,
                                             @RequestBody TeamLinkRequest linkRequest,
                                             HttpServletRequest request) {
        return ApiResponse.success(ExternalTeamMemberResponse.from(teamService.addMember(
                id,
                linkRequest,
                externalAccessService.requireAdminScope(request, ApiKeyScopes.TEAM_MEMBERS_WRITE))));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable("id") String id,
                                          @PathVariable("userId") String userId,
                                          HttpServletRequest request) {
        teamService.removeMember(id, userId, externalAccessService.requireAdminScope(request, ApiKeyScopes.TEAM_MEMBERS_WRITE));
        return ApiResponse.success();
    }

    @GetMapping("/{id}/projects")
    public ApiResponse<List<ExternalProjectTeamResponse>> listProjects(@PathVariable("id") String id, HttpServletRequest request) {
        externalAccessService.requireScope(request, ApiKeyScopes.TEAM_PROJECTS_READ);
        return ApiResponse.success(teamService.listProjects(id).stream()
                .map(ExternalProjectTeamResponse::from)
                .toList());
    }

    @PostMapping("/{id}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExternalProjectTeamResponse> addProject(@PathVariable("id") String id,
                                               @RequestBody TeamLinkRequest linkRequest,
                                               HttpServletRequest request) {
        return ApiResponse.success(ExternalProjectTeamResponse.from(teamService.addProject(
                id,
                linkRequest,
                externalAccessService.requireScope(request, ApiKeyScopes.TEAM_PROJECTS_WRITE))));
    }

    @DeleteMapping("/{id}/projects/{projectId}")
    public ApiResponse<Void> removeProject(@PathVariable("id") String id,
                                           @PathVariable("projectId") String projectId,
                                           HttpServletRequest request) {
        teamService.removeProject(id, projectId, externalAccessService.requireScope(request, ApiKeyScopes.TEAM_PROJECTS_WRITE));
        return ApiResponse.success();
    }
}
