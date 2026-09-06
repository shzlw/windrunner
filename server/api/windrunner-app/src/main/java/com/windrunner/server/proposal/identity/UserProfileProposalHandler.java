package com.windrunner.server.proposal.identity;

import com.windrunner.server.proposal.ProposalKind;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.user.UserAdminService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
final class UserProfileProposalHandler extends AbstractUserProposalHandler {
    UserProfileProposalHandler(UserAdminService userAdminService, TeamService teamService) {
        super(ProposalKind.USER_PROFILE,
                Set.of("username", "email", "displayName", "title", "bio", "timezone"), userAdminService, teamService);
    }
}
