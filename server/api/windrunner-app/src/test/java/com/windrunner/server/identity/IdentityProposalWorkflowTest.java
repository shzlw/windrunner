package com.windrunner.server.identity;

import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectMembershipService;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.user.UserAdminService;
import com.windrunner.server.user.persistence.AppUserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdentityProposalWorkflowTest {
    @Test
    void resolvesEveryIdentityEntityTypeToItsTypedHandler() {
        TeamService teams = mock(TeamService.class);
        AppUserRepository users = mock(AppUserRepository.class);
        IdentityProposalWorkflow workflow = new IdentityProposalWorkflow(
                new TeamProposalHandler(teams, users),
                new TeamMembershipProposalHandler(teams, mock(TeamMemberRepository.class), users),
                new ProjectMembershipProposalHandler(mock(ProjectAccessService.class), mock(ProjectMembershipService.class),
                        mock(ProjectRepository.class), mock(ProjectMemberRepository.class), mock(ProjectTeamRepository.class), teams, users),
                new UserProfileProposalHandler(mock(UserAdminService.class), teams),
                new UserAccessProposalHandler(mock(UserAdminService.class), teams));

        assertThat(workflow.workflowType()).isEqualTo("IDENTITY");
        for (IdentityProposalService.Kind kind : IdentityProposalService.Kind.values()) {
            assertThat(workflow.handler(kind.name()).entityType()).isEqualTo(kind.name());
        }
    }
}
