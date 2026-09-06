package com.windrunner.server.proposal.identity;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalPreparedChange;
import com.windrunner.server.proposal.ProposalDraft;
import com.windrunner.server.proposal.ProposalKind;
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

import static com.windrunner.server.proposal.identity.IdentityProposalUtils.*;

@Component
@RequiredArgsConstructor
final class TeamMembershipProposalHandler implements ProposalHandler<ProposalDraft> {
    private final TeamService teamService;
    private final TeamMemberRepository teamMemberRepository;
    private final AppUserRepository appUserRepository;

    @Override
    public String getEntityType() {
        return ProposalKind.TEAM_MEMBERSHIP.name();
    }

    @Override
    public void authorize(ProposalDraft draft, AppUser actor) {
        teamService.requireAdmin(actor);
    }

    @Override
    public ProposalPreparedChange prepare(ProposalDraft draft, AppUser actor) {
        String teamId = requireValue(draft.teamId(), "Team ID");
        String userId = requireValue(draft.userId(), "User ID");
        Team team = teamService.getTeam(teamId);
        AppUser user = requireMemberUser(userId);
        TeamMember existing = teamMemberRepository.findByTeamIdAndUserId(teamId, userId).orElse(null);
        String oldRole = existing == null ? null : existing.getRole();
        validateMembershipAction(draft.action(), oldRole);
        String newRole = "REMOVE".equals(draft.action()) ? null : normalizeTeamRole(draft.role());
        if (TeamRoles.TEAM_OWNER.equals(oldRole) && !TeamRoles.TEAM_OWNER.equals(newRole) && teamMemberRepository.countOwners(teamId) <= 1) {
            throw createBadRequestException("At least one team owner is required");
        }

        Map<String, String> before = createIdentityMap("teamId", teamId, "team", team.getName(), "userId", userId, "user", getDisplayName(user));
        before.put("role", oldRole);
        putRevision(before, "updatedAt", team.getUpdatedAt());
        putRevision(before, "membershipUpdatedAt", existing == null ? null : existing.getUpdatedAt());
        Map<String, String> after = new LinkedHashMap<>(before);
        after.put("role", newRole);
        return new ProposalPreparedChange(before, after);
    }

    @Override
    public void apply(ProposalDraft draft, ProposalPreparedChange prepared, AppUser actor) {
        teamService.applyMembershipOptimistic(draft.teamId(), draft.userId(), prepared.after().get("role"), draft.action(),
                parseTimestamp(prepared.before().get("updatedAt")), parseTimestamp(prepared.before().get("membershipUpdatedAt")), actor);
    }

    private AppUser requireMemberUser(String id) {
        AppUser user = appUserRepository.findById(id).orElseThrow(() -> createResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (AppRoles.isSuperAdmin(user.getGlobalRole())) {
            throw createBadRequestException("Super admin users cannot be members");
        }
        return user;
    }
}
