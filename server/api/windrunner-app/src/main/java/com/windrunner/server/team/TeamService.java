package com.windrunner.server.team;

import com.windrunner.server.audit.*;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.api.CreateTeamRequest;
import com.windrunner.server.team.api.TeamJoinDecisionRequest;
import com.windrunner.server.team.api.TeamLinkRequest;
import com.windrunner.server.team.domain.ProjectTeam;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamJoinRequest;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamJoinRequestRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RequiredArgsConstructor
@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamJoinRequestRepository teamJoinRequestRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final AppUserRepository appUserRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final AuditLogService auditLogService;

    private final EntityIdGenerator idGenerator;

    private final com.windrunner.server.work.persistence.WorkItemAssigneeRepository workItemAssigneeRepository;

    public List<Team> listTeams(String currentUserId) {
        List<Team> teams = teamRepository.findAllOrdered();
        if (teams.isEmpty()) return teams;
        Map<String, List<TeamMember>> membersByTeamId = new LinkedHashMap<>();
        teamMemberRepository.findByTeamIds(teams.stream().map(Team::getId).toList())
                .forEach(member -> membersByTeamId.computeIfAbsent(member.getTeamId(), ignored -> new java.util.ArrayList<>()).add(member));
        Map<String, String> displayNamesByUserId = new LinkedHashMap<>();
        appUserRepository.findAllById(membersByTeamId.values().stream().flatMap(List::stream).map(TeamMember::getUserId).distinct().toList())
                .forEach(user -> displayNamesByUserId.put(user.getId(), user.getDisplayName() != null && !user.getDisplayName().isBlank() ? user.getDisplayName().trim() : user.getUsername()));
        Map<String, Integer> projectCountsByTeamId = new LinkedHashMap<>();
        projectTeamRepository.findByTeamIds(teams.stream().map(Team::getId).toList())
                .forEach(projectTeam -> projectCountsByTeamId.merge(projectTeam.getTeamId(), 1, Integer::sum));
        teams.forEach(team -> {
            List<TeamMember> members = membersByTeamId.getOrDefault(team.getId(), List.of());
            team.setMemberUserIds(members.stream().map(TeamMember::getUserId).toList());
            Map<String, String> memberDisplayNames = new LinkedHashMap<>();
            members.forEach(member -> memberDisplayNames.put(member.getUserId(), displayNamesByUserId.getOrDefault(member.getUserId(), "Unknown user")));
            team.setMemberDisplayNames(memberDisplayNames);
            team.setProjectCount(projectCountsByTeamId.getOrDefault(team.getId(), 0));
            team.setCurrentUserRole(members.stream().filter(member -> member.getUserId().equals(currentUserId)).map(TeamMember::getRole).findFirst().orElse(null));
        });
        return teams;
    }

    public Team getTeam(String id) {
        return requireTeam(id);
    }

    @Transactional
    public Team createTeam(CreateTeamRequest createRequest, AppUser actor) {
        if (createRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        Team team = new Team();
        team.setName(createRequest.name());
        validateTeam(team, null);
        List<String> ownerUserIds = requireOwnerUserIds(createRequest.ownerUserIds());
        if (team.getId() == null || team.getId().isBlank()) {
            team.setId(idGenerator.generate(EntityIdType.TEAM));
        }
        teamRepository.insert(team.getId(), team.getName());
        for (String ownerUserId : ownerUserIds) {
            teamMemberRepository.insert(team.getId(), ownerUserId, TeamRoles.TEAM_OWNER);
        }
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor == null ? null : actor.getId(),
                AuditActions.CREATE,
                AuditEntityTypes.TEAM,
                team.getId(),
                null,
                AuditOutcomes.SUCCESS,
                "Created team " + team.getName(),
                null,
                auditLogService.json(teamSnapshot(team)),
                null,
                null));
        return team;
    }

    @Transactional
    public Team updateTeam(String id, Team team, AppUser actor) {
        Team beforeTeam = requireTeam(id);
        Map<String, Object> before = teamSnapshot(beforeTeam);
        validateTeam(team, id);
        if (teamRepository.update(id, team.getName()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found");
        }
        team.setId(id);
        Map<String, Object> after = teamSnapshot(team);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor == null ? null : actor.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.TEAM,
                team.getId(),
                null,
                AuditOutcomes.SUCCESS,
                "Updated team " + team.getName(),
                auditLogService.json(before),
                auditLogService.json(after),
                auditLogService.changes(before, after),
                null));
        return team;
    }

    @Transactional
    public void deleteTeam(String id, AppUser actor) {
        Team team = requireTeam(id);
        Map<String, Object> before = teamSnapshot(team);
        for (ProjectTeam projectTeam : projectTeamRepository.findByTeamId(id)) {
            projectAccessService.requireAnotherOwnerBeforeRemovingOwner(
                    projectTeam.getProjectId(),
                    ProjectRoles.OWNER.equals(projectTeam.getRole())
            );
        }
        teamMemberRepository.deleteByTeamId(id);
        projectTeamRepository.deleteByTeamId(id);
        workItemAssigneeRepository.deleteByAssignee("TEAM", id);
        teamRepository.deleteById(id);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor == null ? null : actor.getId(),
                AuditActions.DELETE,
                AuditEntityTypes.TEAM,
                team.getId(),
                null,
                AuditOutcomes.SUCCESS,
                "Deleted team " + team.getName(),
                auditLogService.json(before),
                null,
                null,
                null));
    }

    public List<TeamMember> listMembers(String teamId) {
        requireTeam(teamId);
        return teamMemberRepository.findByTeamId(teamId);
    }

    @Transactional
    public TeamMember addMember(String teamId, TeamLinkRequest linkRequest, AppUser actor) {
        Team team = requireTeam(teamId);
        String userId = requireText(linkRequest == null ? null : linkRequest.userId(), "User id is required");
        String role = normalizeTeamRole(linkRequest == null ? null : linkRequest.role());
        requireNonSuperAdminUser(userId, "Super admin users cannot be added as team members");
        teamMemberRepository.insert(teamId, userId, role);
        TeamMember teamMember = new TeamMember();
        teamMember.setTeamId(teamId);
        teamMember.setUserId(userId);
        teamMember.setRole(role);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor == null ? null : actor.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.TEAM,
                teamId,
                null,
                AuditOutcomes.SUCCESS,
                "Added user to team " + team.getName(),
                null,
                null,
                null,
                auditLogService.json(Map.of("operation", "ADD_MEMBER", "userId", userId, "role", role))));
        return teamMember;
    }

    @Transactional
    public void removeMember(String teamId, String userId, AppUser actor) {
        Team team = requireTeam(teamId);
        teamMemberRepository.delete(teamId, userId);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor == null ? null : actor.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.TEAM,
                teamId,
                null,
                AuditOutcomes.SUCCESS,
                "Removed user from team " + team.getName(),
                null,
                null,
                null,
                auditLogService.json(Map.of("operation", "REMOVE_MEMBER", "userId", userId))));
    }

    public List<ProjectTeam> listProjects(String teamId) {
        requireTeam(teamId);
        return projectTeamRepository.findByTeamId(teamId);
    }

    @Transactional
    public ProjectTeam addProject(String teamId, TeamLinkRequest linkRequest, AppUser actor) {
        Team team = requireTeam(teamId);
        String projectId = requireText(linkRequest == null ? null : linkRequest.projectId(), "Project id is required");
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.OWNER);
        String role = normalizeProjectRole(linkRequest == null ? null : linkRequest.role());
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        ProjectTeam existing = projectTeamRepository.findByProjectIdAndTeamId(projectId, teamId).orElse(null);
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(
                projectId,
                existing != null && ProjectRoles.OWNER.equals(existing.getRole()) && !ProjectRoles.OWNER.equals(role)
        );
        projectTeamRepository.upsert(projectId, teamId, role);
        ProjectTeam projectTeam = new ProjectTeam();
        projectTeam.setProjectId(projectId);
        projectTeam.setTeamId(teamId);
        projectTeam.setRole(role);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor == null ? null : actor.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.TEAM,
                teamId,
                projectId,
                AuditOutcomes.SUCCESS,
                "Linked team " + team.getName() + " to project",
                null,
                null,
                null,
                auditLogService.json(Map.of("operation", "ADD_PROJECT", "projectId", projectId, "role", role))));
        return projectTeam;
    }

    @Transactional
    public void removeProject(String teamId, String projectId, AppUser actor) {
        Team team = requireTeam(teamId);
        projectAccessService.requireProjectRole(projectId, actor, ProjectRoles.OWNER);
        ProjectTeam existing = projectTeamRepository.findByProjectIdAndTeamId(projectId, teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project team not found"));
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(projectId, ProjectRoles.OWNER.equals(existing.getRole()));
        projectTeamRepository.delete(projectId, teamId);
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor == null ? null : actor.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.TEAM,
                teamId,
                projectId,
                AuditOutcomes.SUCCESS,
                "Unlinked team " + team.getName() + " from project",
                null,
                null,
                null,
                auditLogService.json(Map.of("operation", "REMOVE_PROJECT", "projectId", projectId))));
    }

    public List<TeamJoinRequest> listPendingJoinRequests(String teamId, AppUser actor) {
        requireTeamManager(teamId, actor);
        return teamJoinRequestRepository.findPendingByTeamId(teamId);
    }

    @Transactional
    public TeamJoinRequest requestJoin(String teamId, AppUser actor) {
        requireTeam(teamId);
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (AppRoles.isSuperAdmin(actor.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Super admin users have full platform access and cannot request team membership");
        }
        if (teamMemberRepository.findByTeamId(teamId).stream().anyMatch(member -> actor.getId().equals(member.getUserId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already a team member");
        }
        if (teamJoinRequestRepository.hasPendingRequest(teamId, actor.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Join request already exists");
        }

        TeamJoinRequest request = new TeamJoinRequest();
        request.setId(idGenerator.generate(EntityIdType.TEAM_JOIN_REQUEST));
        request.setTeamId(teamId);
        request.setUserId(actor.getId());
        request.setStatus(TeamJoinRequestStatuses.PENDING);
        teamJoinRequestRepository.insertPending(request.getId(), teamId, actor.getId());
        auditLogService.logAfterCommit(new AuditLogEntry(
                actor.getId(),
                AuditActions.CREATE,
                AuditEntityTypes.TEAM_JOIN_REQUEST,
                request.getId(),
                null,
                AuditOutcomes.SUCCESS,
                "Requested to join team",
                null,
                auditLogService.json(Map.of("teamId", teamId, "userId", actor.getId(), "status", TeamJoinRequestStatuses.PENDING)),
                null,
                null));
        return request;
    }

    @Transactional
    public TeamJoinRequest decideJoinRequest(String teamId, String requestId, TeamJoinDecisionRequest decisionRequest, AppUser actor) {
        requireTeamManager(teamId, actor);
        TeamJoinRequest request = teamJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Join request not found"));
        if (!teamId.equals(request.getTeamId()) || !TeamJoinRequestStatuses.PENDING.equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Join request not found");
        }

        String decision = decisionRequest == null || decisionRequest.decision() == null
                ? ""
                : decisionRequest.decision().trim().toUpperCase();
        String status = switch (decision) {
            case "APPROVE", "APPROVED" -> TeamJoinRequestStatuses.APPROVED;
            case "REJECT", "REJECTED" -> TeamJoinRequestStatuses.REJECTED;
            default ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Join request decision must be APPROVE or REJECT");
        };

        if (TeamJoinRequestStatuses.APPROVED.equals(status)) {
            requireNonSuperAdminUser(request.getUserId(), "Super admin users cannot be added as team members");
        }

        if (teamJoinRequestRepository.decide(requestId, status, actor.getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Join request not found");
        }
        request.setStatus(status);
        request.setDecidedByUserId(actor.getId());

        if (TeamJoinRequestStatuses.APPROVED.equals(status)) {
            teamMemberRepository.insert(teamId, request.getUserId(), TeamRoles.TEAM_MEMBER);
        }

        auditLogService.logAfterCommit(new AuditLogEntry(
                actor.getId(),
                AuditActions.UPDATE,
                AuditEntityTypes.TEAM_JOIN_REQUEST,
                request.getId(),
                null,
                AuditOutcomes.SUCCESS,
                status + " team join request",
                null,
                auditLogService.json(Map.of("teamId", teamId, "userId", request.getUserId(), "status", status)),
                null,
                null));
        return request;
    }

    private void validateTeam(Team team, String existingId) {
        if (team == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        String name = requireText(team.getName(), "Team name is required");
        teamRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (existingId == null || !existingId.equals(existing.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team name already exists");
            }
        });
        team.setName(name);
    }

    private List<String> requireOwnerUserIds(List<String> ownerUserIds) {
        if (ownerUserIds == null || ownerUserIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one team owner is required");
        }

        Set<String> distinctOwnerUserIds = new LinkedHashSet<>();
        for (String ownerUserId : ownerUserIds) {
            distinctOwnerUserIds.add(requireText(ownerUserId, "Team owner user id is required"));
        }
        for (String ownerUserId : distinctOwnerUserIds) {
            requireNonSuperAdminUser(ownerUserId, "Super admin users cannot be team owners");
        }
        return List.copyOf(distinctOwnerUserIds);
    }

    private void requireNonSuperAdminUser(String userId, String message) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (AppRoles.isSuperAdmin(user.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private Team requireTeam(String id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
    }

    private void requireTeamManager(String teamId, AppUser actor) {
        requireTeam(teamId);
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (AppRoles.isAdminLike(actor.getGlobalRole()) || teamMemberRepository.isTeamOwner(teamId, actor.getId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Team owner access is required");
    }

    private String normalizeTeamRole(String role) {
        try {
            return TeamRoles.normalize(role);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private String normalizeProjectRole(String role) {
        try {
            return ProjectRoles.normalize(role);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private Map<String, Object> teamSnapshot(Team team) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", team.getId());
        snapshot.put("name", team.getName());
        return snapshot;
    }
}
