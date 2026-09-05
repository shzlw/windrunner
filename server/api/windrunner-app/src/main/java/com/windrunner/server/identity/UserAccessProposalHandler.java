package com.windrunner.server.identity;

import com.windrunner.server.team.TeamService;
import com.windrunner.server.user.UserAdminService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
final class UserAccessProposalHandler extends AbstractUserProposalHandler {
    UserAccessProposalHandler(UserAdminService users, TeamService teams) {
        super(IdentityProposalService.Kind.USER_ACCESS, Set.of("status", "globalRole"), users, teams);
    }
}
