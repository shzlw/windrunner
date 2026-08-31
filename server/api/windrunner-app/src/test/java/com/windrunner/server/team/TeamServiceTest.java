package com.windrunner.server.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.windrunner.server.audit.AuditLogService;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.api.CreateTeamRequest;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamJoinRequestRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.team.persistence.TeamRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TeamJoinRequestRepository teamJoinRequestRepository;
    @Mock
    private ProjectTeamRepository projectTeamRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private com.windrunner.server.work.persistence.WorkItemAssigneeRepository workItemAssigneeRepository;
    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(
                teamRepository,
                teamMemberRepository,
                teamJoinRequestRepository,
                projectTeamRepository,
                appUserRepository,
                projectRepository,
                projectAccessService,
                auditLogService,
                new EntityIdGenerator(),
                workItemAssigneeRepository);
    }

    @Test
    void createTeamRequiresAtLeastOneOwner() {
        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> teamService.createTeam(new CreateTeamRequest("Platform", List.of()), actor()));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("At least one team owner is required");
        verifyNoInteractions(teamMemberRepository);
    }

    @Test
    void createTeamAssignsEverySelectedOwner() {
        when(teamRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(appUserRepository.findById("owner-1")).thenReturn(Optional.of(user("owner-1")));
        when(appUserRepository.findById("owner-2")).thenReturn(Optional.of(user("owner-2")));

        Team createdTeam = teamService.createTeam(
                new CreateTeamRequest("Platform", List.of("owner-1", "owner-2")), actor());

        ArgumentCaptor<String> teamId = ArgumentCaptor.forClass(String.class);
        verify(teamRepository).insert(teamId.capture(), anyString(), any());
        assertThat(createdTeam.getId()).isEqualTo(teamId.getValue());
        assertThat(createdTeam.getId()).startsWith("team_");
        verify(teamMemberRepository).insert(teamId.getValue(), "owner-1", TeamRoles.TEAM_OWNER);
        verify(teamMemberRepository).insert(teamId.getValue(), "owner-2", TeamRoles.TEAM_OWNER);
    }

    @Test
    void removeMemberRejectsMissingMembership() {
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team("team-1")));
        when(teamMemberRepository.findByTeamIdAndUserId("team-1", "user-1")).thenReturn(Optional.empty());

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> teamService.removeMember("team-1", "user-1", actor()));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void removeMemberRejectsLastOwner() {
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team("team-1")));
        TeamMember owner = new TeamMember();
        owner.setTeamId("team-1");
        owner.setUserId("user-1");
        owner.setRole(TeamRoles.TEAM_OWNER);
        when(teamMemberRepository.findByTeamIdAndUserId("team-1", "user-1")).thenReturn(Optional.of(owner));
        when(teamMemberRepository.countOwners("team-1")).thenReturn(1L);

        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class,
                () -> teamService.removeMember("team-1", "user-1", actor()));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void removeMemberUsesConditionalDeleteWhenAnotherOwnerExists() {
        when(teamRepository.findById("team-1")).thenReturn(Optional.of(team("team-1")));
        TeamMember owner = new TeamMember();
        owner.setTeamId("team-1");
        owner.setUserId("user-1");
        owner.setRole(TeamRoles.TEAM_OWNER);
        when(teamMemberRepository.findByTeamIdAndUserId("team-1", "user-1")).thenReturn(Optional.of(owner));
        when(teamMemberRepository.countOwners("team-1")).thenReturn(2L);
        when(teamMemberRepository.deleteIfNotLastOwner("team-1", "user-1")).thenReturn(1);

        teamService.removeMember("team-1", "user-1", actor());

        verify(teamMemberRepository).deleteIfNotLastOwner("team-1", "user-1");
    }

    private AppUser actor() {
        AppUser actor = new AppUser();
        actor.setId("admin-1");
        return actor;
    }

    private AppUser user(String id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setGlobalRole("USER");
        return user;
    }

    private Team team(String id) {
        Team team = new Team();
        team.setId(id);
        team.setName("Platform");
        return team;
    }
}
