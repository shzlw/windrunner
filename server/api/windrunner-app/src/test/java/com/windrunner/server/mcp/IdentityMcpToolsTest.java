package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.tools.calendar.FetchTeamCalendarWorkloadTool;
import com.windrunner.server.tools.identity.FetchTeamMembersTool;
import com.windrunner.server.tools.identity.FetchTeamDetailsTool;
import com.windrunner.server.tools.identity.FetchTeamProjectsTool;
import com.windrunner.server.tools.identity.FetchTeamsTool;
import com.windrunner.server.tools.identity.FetchUserDetailsTool;
import com.windrunner.server.tools.identity.FetchUsersTool;
import com.windrunner.server.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityMcpToolsTest {

    @Mock
    private McpAuthorization authorization;
    @Mock
    private FetchTeamsTool teams;
    @Mock
    private FetchTeamDetailsTool teamDetails;
    @Mock
    private FetchTeamMembersTool teamMembers;
    @Mock
    private FetchTeamProjectsTool teamProjects;
    @Mock
    private FetchUsersTool users;
    @Mock
    private FetchUserDetailsTool userDetails;
    @Mock
    private FetchTeamCalendarWorkloadTool teamCalendarWorkload;

    @Test
    void listTeamsDelegatesToBoundedProgressiveTool() throws Exception {
        FetchTeamsTool.Result expected = new FetchTeamsTool.Result(List.of(), 0, 5);
        when(teams.execute(any(FetchTeamsTool.Parameters.class), any())).thenReturn(expected);

        FetchTeamsTool.Result result = tools().listTeams("SRE", 5);

        assertThat(result).isSameAs(expected);
        verify(authorization).requireScope(ApiKeyScopes.TEAMS_READ);
        ArgumentCaptor<FetchTeamsTool.Parameters> parameters = ArgumentCaptor.forClass(FetchTeamsTool.Parameters.class);
        verify(teams).execute(parameters.capture(), any());
        assertThat(parameters.getValue()).isEqualTo(new FetchTeamsTool.Parameters("SRE", 5));
    }

    @Test
    void listTeamMembersPreservesPageArguments() throws Exception {
        when(teamMembers.execute(any(FetchTeamMembersTool.Parameters.class), any()))
                .thenReturn(new FetchTeamMembersTool.Result("team-1", "SRE", List.of(), 0, 125, 20, 40, true));

        FetchTeamMembersTool.Result result = tools().listTeamMembers("team-1", 20, 40);

        assertThat(result.total()).isEqualTo(125);
        assertThat(result.hasMore()).isTrue();
        verify(authorization).requireScope(ApiKeyScopes.TEAM_MEMBERS_READ);
        verify(teamMembers).execute(eq(new FetchTeamMembersTool.Parameters("team-1", 20, 40)), any());
    }

    @Test
    void getUserReturnsTargetedProfileDetails() throws Exception {
        FetchUserDetailsTool.ResultUser user = new FetchUserDetailsTool.ResultUser(
                "user-1", "jane", "Jane Doe", "jane@example.com", "Product", "Owns discovery.");
        when(userDetails.execute(any(FetchUserDetailsTool.Parameters.class), any()))
                .thenReturn(new FetchUserDetailsTool.Result(List.of(user), 1, 1));

        IdentityMcpTools.UserDetails result = tools().getUser("user-1");

        assertThat(result).isEqualTo(new IdentityMcpTools.UserDetails(
                "user-1", "jane", "Jane Doe", "jane@example.com", "Product", "Owns discovery."));
        verify(authorization).requireScope(ApiKeyScopes.USERS_READ);
        verify(userDetails).execute(eq(new FetchUserDetailsTool.Parameters(List.of("user-1"))), any());
    }

    @Test
    void teamWorkloadRequiresAllUnderlyingReadScopes() throws Exception {
        AppUser actor = new AppUser();
        actor.setId("user-1");
        ToolExecutionContext context = new ToolExecutionContext(actor, null, List.of("project-1"));
        when(authorization.requireScopes(
                ApiKeyScopes.TEAMS_READ,
                ApiKeyScopes.TEAM_MEMBERS_READ,
                ApiKeyScopes.WORK_ITEMS_READ,
                ApiKeyScopes.CALENDAR_EVENTS_READ)).thenReturn(actor);
        when(authorization.toolContext(actor)).thenReturn(context);
        when(teamCalendarWorkload.execute(any(FetchTeamCalendarWorkloadTool.Parameters.class), eq(context)))
                .thenReturn(null);
        LocalDate from = LocalDate.of(2026, 9, 14);
        LocalDate to = LocalDate.of(2026, 9, 20);

        assertThat(tools().getTeamWorkload("team-1", from, to)).isNull();

        verify(teamCalendarWorkload).execute(
                eq(new FetchTeamCalendarWorkloadTool.Parameters("team-1", from, to)), eq(context));
    }

    private IdentityMcpTools tools() {
        return new IdentityMcpTools(
                authorization, teams, teamDetails, teamMembers, teamProjects, users, userDetails, teamCalendarWorkload);
    }
}
