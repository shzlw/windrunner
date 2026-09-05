package com.windrunner.server.proposal.identity;

import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalPreparedChange;
import com.windrunner.server.proposal.ProposalService;
import com.windrunner.server.team.TeamRoles;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.domain.TeamMember;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.windrunner.server.proposal.identity.IdentityProposalSupport.*;

@Component
@RequiredArgsConstructor
final class TeamMembershipProposalHandler implements ProposalHandler<ProposalService.Draft> {
    private final TeamService teamService;
    private final TeamMemberRepository teamMemberRepository;
    private final AppUserRepository appUserRepository;

    @Override
    public String entityType() {
        return ProposalService.Kind.TEAM_MEMBERSHIP.name();
    }

    @Override
    public void authorize(ProposalService.Draft draft, AppUser actor) {
        teamService.requireAdmin(actor);
    }

    @Override
    public ProposalPreparedChange prepare(ProposalService.Draft draft, AppUser actor) {
        String teamId = required(draft.teamId(), "Team ID");
        String userId = required(draft.userId(), "User ID");
        Team team = teamService.getTeam(teamId);
        AppUser user = memberUser(userId);
        TeamMember existing = teamMemberRepository.findByTeamIdAndUserId(teamId, userId).orElse(null);
        String oldRole = existing == null ? null : existing.getRole();
        membershipAction(draft.action(), oldRole);
        String newRole = "REMOVE".equals(draft.action()) ? null : teamRole(draft.role());
        if (TeamRoles.TEAM_OWNER.equals(oldRole) && !TeamRoles.TEAM_OWNER.equals(newRole) && teamMemberRepository.countOwners(teamId) <= 1) {
            throw bad("At least one team owner is required");
        }

        Map<String, String> before = identity("teamId", teamId, "team", team.getName(), "userId", userId, "user", display(user));
        before.put("role", oldRole);
        putRevision(before, "updatedAt", team.getUpdatedAt());
        putRevision(before, "membershipUpdatedAt", existing == null ? null : existing.getUpdatedAt());
        Map<String, String> after = new LinkedHashMap<>(before);
        after.put("role", newRole);
        return new ProposalPreparedChange(before, after);
    }

    @Override
    public void apply(ProposalService.Draft draft, ProposalPreparedChange prepared, AppUser actor) {
        teamService.applyMembershipOptimistic(draft.teamId(), draft.userId(), prepared.after().get("role"), draft.action(),
                timestamp(prepared.before().get("updatedAt")), timestamp(prepared.before().get("membershipUpdatedAt")), actor);
    }

    private AppUser memberUser(String id) {
        AppUser user = appUserRepository.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "User not found"));
        if (com.windrunner.server.auth.security.AppRoles.isSuperAdmin(user.getGlobalRole())) {
            throw bad("Super admin users cannot be members");
        }
        return user;
    }
}
