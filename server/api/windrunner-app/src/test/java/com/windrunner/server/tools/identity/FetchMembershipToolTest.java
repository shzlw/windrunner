package com.windrunner.server.tools.identity;

import com.windrunner.server.project.domain.ProjectMember;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FetchMembershipToolTest {

    @Mock
    private TeamService teams;
    @Mock
    private ToolAuthorizationService authorization;
    @Mock
    private TeamMemberRepository teamMembers;
    @Mock
    private ProjectMemberRepository projectMembers;
    @Mock
    private ProjectTeamRepository projectTeams;

    @Test
    void treatsBlankUnusedProjectSubjectIdAsAbsent() {
        ProjectMember membership = new ProjectMember();
        membership.setRole("EDITOR");
        when(projectMembers.findByProjectIdAndUserId("project-1", "user-1"))
                .thenReturn(Optional.of(membership));

        FetchMembershipTool.Result result = (FetchMembershipTool.Result) tool().execute(
                new FetchMembershipTool.Parameters(" project-1 ", "", " user-1 "), context());

        assertThat(result).isEqualTo(new FetchMembershipTool.Result(
                "project-1", null, "user-1", true, "EDITOR"));
        verify(projectMembers).findByProjectIdAndUserId("project-1", "user-1");
        verifyNoInteractions(projectTeams, teamMembers, teams);
    }

    @Test
    void treatsBlankProjectIdAsAbsentForTeamLookup() {
        TeamMember membership = new TeamMember();
        membership.setRole("TEAM_MEMBER");
        when(teams.getTeam("team-1")).thenReturn(new Team());
        when(teamMembers.findByTeamIdAndUserId("team-1", "user-1"))
                .thenReturn(Optional.of(membership));

        FetchMembershipTool.Result result = (FetchMembershipTool.Result) tool().execute(
                new FetchMembershipTool.Parameters(" ", " team-1 ", " user-1 "), context());

        assertThat(result).isEqualTo(new FetchMembershipTool.Result(
                null, "team-1", "user-1", true, "TEAM_MEMBER"));
        verify(teamMembers).findByTeamIdAndUserId("team-1", "user-1");
        verifyNoInteractions(projectMembers, projectTeams);
    }

    @Test
    void rejectsTwoNonBlankProjectSubjectIds() {
        assertThatThrownBy(() -> tool().execute(
                new FetchMembershipTool.Parameters("project-1", "team-1", "user-1"), context()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Supply exactly one userId or teamId");

        verifyNoInteractions(projectMembers, projectTeams, teamMembers, teams);
    }

    private FetchMembershipTool tool() {
        return new FetchMembershipTool(teams, authorization, teamMembers, projectMembers, projectTeams);
    }

    private static ToolExecutionContext context() {
        AppUser actor = new AppUser();
        actor.setId("actor-1");
        return new ToolExecutionContext(actor, "session-1", List.of("project-1"));
    }
}
