package com.windrunner.server.identity;

import com.windrunner.server.team.TeamService;
import com.windrunner.server.user.UserAdminService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
final class UserProfileProposalHandler extends AbstractUserProposalHandler {
    UserProfileProposalHandler(UserAdminService users, TeamService teams) {
        super(IdentityProposalService.Kind.USER_PROFILE,
                Set.of("username", "email", "displayName", "title", "bio", "timezone"), users, teams);
    }
}
