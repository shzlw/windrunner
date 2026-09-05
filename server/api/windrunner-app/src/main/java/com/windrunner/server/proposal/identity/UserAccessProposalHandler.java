package com.windrunner.server.proposal.identity;

import com.windrunner.server.proposal.ProposalService;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.user.UserAdminService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
final class UserAccessProposalHandler extends AbstractUserProposalHandler {
    UserAccessProposalHandler(UserAdminService userAdminService, TeamService teamService) {
        super(ProposalService.Kind.USER_ACCESS, Set.of("status", "globalRole"), userAdminService, teamService);
    }
}
