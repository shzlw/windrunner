package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.tools.identity.FetchTeamMembersTool;
import com.windrunner.server.tools.identity.FetchTeamsTool;
import com.windrunner.server.tools.identity.FetchUserDetailsTool;
import com.windrunner.server.tools.identity.FetchUsersTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityMcpToolsTest {

    @Mock
    private McpAuthorization authorization;
    @Mock
    private FetchTeamsTool teams;
    @Mock
    private com.windrunner.server.tools.identity.FetchTeamDetailsTool teamDetails;
    @Mock
    private FetchTeamMembersTool teamMembers;
    @Mock
    private com.windrunner.server.tools.identity.FetchTeamProjectsTool teamProjects;
    @Mock
    private FetchUsersTool users;
    @Mock
    private FetchUserDetailsTool userDetails;

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
        verify(teamMembers).execute(new FetchTeamMembersTool.Parameters("team-1", 20, 40), any());
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
        verify(userDetails).execute(new FetchUserDetailsTool.Parameters(List.of("user-1")), any());
    }

    private IdentityMcpTools tools() {
        return new IdentityMcpTools(authorization, teams, teamDetails, teamMembers, teamProjects, users, userDetails);
    }
}
