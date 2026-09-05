package com.windrunner.server.identity;

import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalPreparedChange;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.api.CreateTeamRequest;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.windrunner.server.identity.IdentityProposalSupport.*;

@Component
@RequiredArgsConstructor
final class TeamProposalHandler implements ProposalHandler<IdentityProposalService.Draft> {
    private final TeamService teams;
    private final AppUserRepository users;

    @Override
    public String entityType() {
        return IdentityProposalService.Kind.TEAM.name();
    }

    @Override
    public void authorize(IdentityProposalService.Draft draft, AppUser actor) {
        teams.requireAdmin(actor);
    }

    @Override
    public ProposalPreparedChange prepare(IdentityProposalService.Draft draft, AppUser actor) {
        if ("REMOVE".equals(draft.action())) throw bad("Team deletion is not supported by this tool");
        if ("ADD".equals(draft.action()) && draft.teamId() != null) throw bad("ADD must not supply an existing teamId");
        if ("UPDATE".equals(draft.action()) && draft.ownerUserIds() != null) throw bad("Use team membership proposals to change owners");

        Map<String, String> requested = fields(draft, java.util.Set.of("name", "description"));
        Map<String, String> before = new LinkedHashMap<>();
        Team team = new Team();
        String id = null;
        if ("UPDATE".equals(draft.action())) {
            id = required(draft.teamId(), "Team ID");
            Team current = teams.getTeam(id);
            before.put("teamId", id);
            before.put("name", current.getName());
            before.put("description", current.getDescription());
            putRevision(before, "updatedAt", current.getUpdatedAt());
            team.setName(current.getName());
            team.setDescription(current.getDescription());
        }
        if (requested.containsKey("name")) team.setName(required(requested.get("name"), "Name"));
        if (requested.containsKey("description")) team.setDescription(requested.get("description"));
        teams.validateTeamChange(id, team, draft.ownerUserIds(), actor);

        Map<String, String> after = new LinkedHashMap<>(before);
        after.put("name", team.getName());
        after.put("description", team.getDescription());
        if (id == null) {
            List<String> owners = draft.ownerUserIds() == null ? List.of() : draft.ownerUserIds();
            if (owners.size() > 25) throw bad("At most 25 initial owners are allowed");
            after.put("ownerUserIds", String.join(", ", owners));
            List<String> ownerNames = new ArrayList<>();
            users.findAllById(owners).forEach(user -> ownerNames.add(display(user)));
            Collections.sort(ownerNames);
            after.put("ownerNames", String.join(", ", ownerNames));
        }
        return new ProposalPreparedChange(before, after);
    }

    @Override
    public void apply(IdentityProposalService.Draft draft, ProposalPreparedChange prepared, AppUser actor) {
        if ("ADD".equals(draft.action())) {
            teams.createTeam(new CreateTeamRequest(prepared.after().get("name"), prepared.after().get("description"), draft.ownerUserIds()), actor);
            return;
        }
        Team team = new Team();
        team.setName(prepared.after().get("name"));
        team.setDescription(prepared.after().get("description"));
        teams.updateTeamIfUnchanged(draft.teamId(), team, timestamp(prepared.before().get("updatedAt")), actor);
    }
}
