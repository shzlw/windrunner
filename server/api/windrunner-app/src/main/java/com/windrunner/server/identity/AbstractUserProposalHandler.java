package com.windrunner.server.identity;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalPreparedChange;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.user.UserAdminService;
import com.windrunner.server.user.api.UpdateUserRequest;
import com.windrunner.server.user.api.UserResponse;
import com.windrunner.server.user.domain.AppUser;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.windrunner.server.identity.IdentityProposalSupport.*;

abstract class AbstractUserProposalHandler implements ProposalHandler<IdentityProposalService.Draft> {
    private final IdentityProposalService.Kind kind;
    private final Set<String> allowedFields;
    protected final UserAdminService users;
    private final TeamService teams;

    protected AbstractUserProposalHandler(IdentityProposalService.Kind kind, Set<String> allowedFields,
                                          UserAdminService users, TeamService teams) {
        this.kind = kind;
        this.allowedFields = allowedFields;
        this.users = users;
        this.teams = teams;
    }

    @Override
    public String entityType() {
        return kind.name();
    }

    @Override
    public void authorize(IdentityProposalService.Draft draft, AppUser actor) {
        teams.requireAdmin(actor);
        users.getUser(required(draft.userId(), "User ID"), actor);
        if (kind == IdentityProposalService.Kind.USER_ACCESS && draft.fields() != null
                && draft.fields().containsKey("globalRole") && !AppRoles.isSuperAdmin(actor.getGlobalRole())) {
            throw error(HttpStatus.FORBIDDEN, "Superadmin access is required to update global role");
        }
    }

    @Override
    public ProposalPreparedChange prepare(IdentityProposalService.Draft draft, AppUser actor) {
        if (!"UPDATE".equals(draft.action())) throw bad("User proposals support UPDATE only");
        Map<String, String> requested = fields(draft, allowedFields);
        UserResponse current = users.getUser(draft.userId(), actor);
        Map<String, String> before = identity("userId", current.id(), "username", current.username());
        before.put("email", current.email());
        before.put("displayName", current.displayName());
        before.put("title", current.title());
        before.put("bio", current.bio());
        before.put("timezone", current.timezone());
        before.put("status", current.status());
        before.put("globalRole", current.globalRole());
        putRevision(before, "updatedAt", current.updatedAt());

        Map<String, String> after = new LinkedHashMap<>(before);
        requested.forEach((key, value) -> after.put(key, value == null || value.isBlank() ? null : value.trim()));
        for (String key : List.of("username", "timezone", "status", "globalRole")) required(after.get(key), key);
        UpdateUserRequest request = userRequest(draft, after);
        users.validateUpdate(draft.userId(), request, actor);
        after.put("username", request.getUsername());
        after.put("email", request.getEmail());
        after.put("timezone", request.getTimezone());
        after.put("status", request.getStatus());
        if (requested.containsKey("globalRole")) after.put("globalRole", request.getGlobalRole().trim().toUpperCase(Locale.ROOT));
        return new ProposalPreparedChange(before, after);
    }

    @Override
    public void apply(IdentityProposalService.Draft draft, ProposalPreparedChange prepared, AppUser actor) {
        users.updateUserIfUnchanged(draft.userId(), userRequest(draft, prepared.after()),
                timestamp(prepared.before().get("updatedAt")), actor);
    }
}
