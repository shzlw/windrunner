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
        validateTeamInput(createRequest);
        return ApiResponse.success(ExternalTeamResponse.from(teamService.createTeam(
                createRequest,
                externalAccessService.requireAdminScope(request, ApiKeyScopes.TEAMS_WRITE))));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExternalTeamResponse> updateTeam(@PathVariable("id") String id,
                                        @RequestBody Team team,
                                        HttpServletRequest request) {
        validateTeamInput(team);
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
    public ApiResponse<List<ExternalTeamMemberResponse>> listMembers(@PathVariable("id") String id,
                                                                     @RequestParam(name = "page", defaultValue = "0") int page,
                                                                     @RequestParam(name = "size", defaultValue = "50") int size,
                                                                     HttpServletRequest request) {
        externalAccessService.requireScope(request, ApiKeyScopes.TEAM_MEMBERS_READ);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        long totalItems = teamService.countMembers(id);
        return ApiResponse.page(teamService.listMembersPage(id, normalizedSize,
                        (long) normalizedPage * normalizedSize).stream()
                .map(ExternalTeamMemberResponse::from)
                .toList(), normalizedPage, normalizedSize, totalItems,
                (int) Math.ceil(totalItems / (double) normalizedSize));
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExternalTeamMemberResponse> addMember(@PathVariable("id") String id,
                                             @RequestBody TeamLinkRequest linkRequest,
                                             HttpServletRequest request) {
        validateMemberLink(linkRequest);
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
    public ApiResponse<List<ExternalProjectTeamResponse>> listProjects(@PathVariable("id") String id,
                                                                        @RequestParam(name = "page", defaultValue = "0") int page,
                                                                        @RequestParam(name = "size", defaultValue = "50") int size,
                                                                        HttpServletRequest request) {
        externalAccessService.requireScope(request, ApiKeyScopes.TEAM_PROJECTS_READ);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        long totalItems = teamService.countProjects(id);
        return ApiResponse.page(teamService.listProjectsPage(id, normalizedSize,
                        (long) normalizedPage * normalizedSize).stream()
                .map(ExternalProjectTeamResponse::from)
                .toList(), normalizedPage, normalizedSize, totalItems,
                (int) Math.ceil(totalItems / (double) normalizedSize));
    }

    private void validateTeamInput(CreateTeamRequest request) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required");
        }
        ExternalInputValidation.requireMaxLength(request.name(), "Team name", ExternalInputValidation.MAX_NAME_LENGTH);
        ExternalInputValidation.requireMaxLength(request.description(), "Team description", ExternalInputValidation.MAX_DESCRIPTION_LENGTH);
        ExternalInputValidation.requireMaxSize(request.ownerUserIds(), "Owner ids", ExternalInputValidation.MAX_ID_LIST_SIZE);
        if (request.ownerUserIds() != null) {
            request.ownerUserIds().forEach(ownerId -> ExternalInputValidation.requiredText(
                    ownerId, "Team owner user id", ExternalInputValidation.MAX_ID_LENGTH));
        }
    }

    private void validateTeamInput(Team team) {
        if (team == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required");
        }
        ExternalInputValidation.requireMaxLength(team.getName(), "Team name", ExternalInputValidation.MAX_NAME_LENGTH);
        ExternalInputValidation.requireMaxLength(team.getDescription(), "Team description", ExternalInputValidation.MAX_DESCRIPTION_LENGTH);
    }

    private void validateMemberLink(TeamLinkRequest request) {
        if (request == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Request body is required");
        }
        ExternalInputValidation.requiredText(request.userId(), "User id", ExternalInputValidation.MAX_ID_LENGTH);
    }

}
