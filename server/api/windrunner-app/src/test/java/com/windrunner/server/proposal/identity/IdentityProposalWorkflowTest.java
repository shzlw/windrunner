package com.windrunner.server.proposal.identity;

import com.windrunner.server.proposal.ProposalKind;
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
        TeamService teamService = mock(TeamService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        IdentityProposalWorkflow workflow = new IdentityProposalWorkflow(
                new TeamProposalHandler(teamService, appUserRepository),
                new TeamMembershipProposalHandler(teamService, mock(TeamMemberRepository.class), appUserRepository),
                new ProjectMembershipProposalHandler(mock(ProjectAccessService.class), mock(ProjectMembershipService.class),
                        mock(ProjectRepository.class), mock(ProjectMemberRepository.class), mock(ProjectTeamRepository.class), teamService, appUserRepository),
                new UserProfileProposalHandler(mock(UserAdminService.class), teamService),
                new UserAccessProposalHandler(mock(UserAdminService.class), teamService));

        assertThat(workflow.getWorkflowType()).isEqualTo("IDENTITY");
        for (ProposalKind kind : ProposalKind.values()) {
            assertThat(workflow.handler(kind.name()).getEntityType()).isEqualTo(kind.name());
        }
    }
}
