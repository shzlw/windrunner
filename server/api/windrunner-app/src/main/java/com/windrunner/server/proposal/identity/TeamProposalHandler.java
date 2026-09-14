package com.windrunner.server.proposal.identity;

import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalChange;
import com.windrunner.server.proposal.ProposalPreparedChange;
import com.windrunner.server.proposal.ProposalDraft;
import com.windrunner.server.proposal.ProposalKind;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.api.CreateTeamRequest;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.windrunner.server.proposal.identity.IdentityProposalUtils.createBadRequestException;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.extractFields;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.getDisplayName;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.parseSnapshot;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.parseTimestamp;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.putRevision;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.requireCurrentValue;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.requireValue;

@Component
@RequiredArgsConstructor
final class TeamProposalHandler implements ProposalHandler<ProposalDraft, ProposalPreparedChange> {
    private final TeamService teamService;
    private final AppUserRepository appUserRepository;

    @Override
    public String getEntityType() {
        return ProposalKind.TEAM.name();
    }

    @Override
    public void authorize(ProposalDraft draft, AppUser actor) {
        teamService.requireAdmin(actor);
    }

    @Override
    public ProposalPreparedChange prepare(ProposalDraft draft, AppUser actor) {
        if ("REMOVE".equals(draft.action())) throw createBadRequestException("Team deletion is not supported by this tool");
        if ("ADD".equals(draft.action()) && draft.teamId() != null) throw createBadRequestException("ADD must not supply an existing teamId");
        if ("UPDATE".equals(draft.action()) && draft.ownerUserIds() != null)
            throw createBadRequestException("Use team membership proposals to change owners");

        Map<String, String> requested = extractFields(draft, Set.of("name", "description"));
        Map<String, String> before = new LinkedHashMap<>();
        Team team = new Team();
        String id = null;
        if ("UPDATE".equals(draft.action())) {
            id = requireValue(draft.teamId(), "Team ID");
        Team current = teamService.getTeam(id);
            before.put("teamId", id);
            before.put("name", current.getName());
            before.put("description", current.getDescription());
            putRevision(before, "updatedAt", current.getUpdatedAt());
            team.setName(current.getName());
            team.setDescription(current.getDescription());
        }
        if (requested.containsKey("name")) team.setName(requireValue(requested.get("name"), "Name"));
        if (requested.containsKey("description")) team.setDescription(requested.get("description"));
        teamService.validateTeamChange(id, team, draft.ownerUserIds(), actor);

        Map<String, String> after = new LinkedHashMap<>(before);
        after.put("name", team.getName());
        after.put("description", team.getDescription());
        if (id == null) {
            List<String> owners = draft.ownerUserIds() == null ? List.of() : draft.ownerUserIds();
            if (owners.size() > 25) throw createBadRequestException("At most 25 initial owners are allowed");
            after.put("ownerUserIds", String.join(", ", owners));
            List<String> ownerNames = new ArrayList<>();
            appUserRepository.findAllById(owners).forEach(user -> ownerNames.add(getDisplayName(user)));
            Collections.sort(ownerNames);
            after.put("ownerNames", String.join(", ", ownerNames));
        }
        return new ProposalPreparedChange(before, after);
    }

    @Override
    public void apply(ProposalDraft draft, ProposalPreparedChange prepared, AppUser actor) {
        if ("ADD".equals(draft.action())) {
            teamService.createTeam(new CreateTeamRequest(prepared.after().get("name"), prepared.after().get("description"), draft.ownerUserIds()), actor);
            return;
        }
        Team team = new Team();
        team.setName(prepared.after().get("name"));
        team.setDescription(prepared.after().get("description"));
        teamService.updateTeamIfUnchanged(draft.teamId(), team, parseTimestamp(prepared.before().get("updatedAt")), actor);
    }

    @Override
    public ProposalDraft buildRevert(ProposalChange appliedChange, AppUser actor) {
        if (!"UPDATE".equals(appliedChange.getOperation())) {
            throw createBadRequestException("Team creation cannot be safely reverted because the team may now be in use");
        }
        ProposalDraft original = JsonUtils.fromJson(appliedChange.getPayload(), ProposalDraft.class);
        Map<String, String> before = parseSnapshot(appliedChange.getBeforeSnapshot());
        Map<String, String> after = parseSnapshot(appliedChange.getAfterSnapshot());
        Team current = teamService.getTeam(original.teamId());
        Map<String, String> restoreFields = new LinkedHashMap<>();
        if (!Objects.equals(before.get("name"), after.get("name"))) {
            requireCurrentValue("team name", current.getName(), after.get("name"));
            restoreFields.put("name", before.get("name"));
        }
        if (!Objects.equals(before.get("description"), after.get("description"))) {
            requireCurrentValue("team description", current.getDescription(), after.get("description"));
            restoreFields.put("description", before.get("description"));
        }
        if (restoreFields.isEmpty()) {
            throw createBadRequestException("This team proposal has no reversible changes");
        }
        return new ProposalDraft("UPDATE", original.teamId(), null, null, null, null, null, restoreFields);
    }
}
