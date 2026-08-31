package com.windrunner.server.external.v1.api;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.external.v1.dto.ExternalProjectTeamResponse;
import com.windrunner.server.external.v1.dto.ExternalTeamMemberResponse;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.domain.ProjectTeam;
import com.windrunner.server.team.domain.TeamMember;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalTeamControllerTest {

    @Mock
    private TeamService teamService;
    @Mock
    private com.windrunner.server.team.persistence.TeamRepository teamRepository;
    @Mock
    private ExternalAccessService externalAccessService;
    @Mock
    private HttpServletRequest request;

    @Test
    void listMembersReturnsPagedEnvelope() {
        TeamMember member = new TeamMember();
        member.setTeamId("team-1");
        member.setUserId("user-1");
        member.setRole("TEAM_MEMBER");
        when(teamService.countMembers("team-1")).thenReturn(101L);
        when(teamService.listMembersPage("team-1", 100, 100L)).thenReturn(List.of(member));

        ApiResponse<List<ExternalTeamMemberResponse>> response = controller().listMembers("team-1", 1, 500, request);

        verify(externalAccessService).requireScope(request, ApiKeyScopes.TEAM_MEMBERS_READ);
        assertThat(response.data()).hasSize(1);
        assertThat(response.meta().page()).isEqualTo(1);
        assertThat(response.meta().size()).isEqualTo(100);
        assertThat(response.meta().totalItems()).isEqualTo(101L);
        assertThat(response.meta().totalPages()).isEqualTo(2);
    }

    @Test
    void listProjectsReturnsPagedEnvelope() {
        ProjectTeam link = new ProjectTeam();
        link.setProjectId("proj-1");
        link.setTeamId("team-1");
        link.setRole("VIEWER");
        when(teamService.countProjects("team-1")).thenReturn(1L);
        when(teamService.listProjectsPage("team-1", 50, 0L)).thenReturn(List.of(link));

        ApiResponse<List<ExternalProjectTeamResponse>> response = controller().listProjects("team-1", 0, 50, request);

        verify(externalAccessService).requireScope(request, ApiKeyScopes.TEAM_PROJECTS_READ);
        assertThat(response.data()).containsExactly(ExternalProjectTeamResponse.from(link));
        assertThat(response.meta().totalItems()).isEqualTo(1L);
    }

    private ExternalTeamController controller() {
        return new ExternalTeamController(teamService, teamRepository, externalAccessService);
    }
}
