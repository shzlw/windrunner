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
        when(teamRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
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
}
