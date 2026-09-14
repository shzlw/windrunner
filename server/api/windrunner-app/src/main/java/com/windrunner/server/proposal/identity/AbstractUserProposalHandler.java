package com.windrunner.server.proposal.identity;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalChange;
import com.windrunner.server.proposal.ProposalPreparedChange;
import com.windrunner.server.proposal.ProposalDraft;
import com.windrunner.server.proposal.ProposalKind;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.user.UserAdminService;
import com.windrunner.server.user.api.UpdateUserRequest;
import com.windrunner.server.user.api.UserResponse;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.utils.JsonUtils;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.windrunner.server.proposal.identity.IdentityProposalUtils.buildUserUpdateRequest;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.createBadRequestException;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.createIdentityMap;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.createResponseStatusException;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.extractFields;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.parseSnapshot;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.parseTimestamp;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.putRevision;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.requireCurrentValue;
import static com.windrunner.server.proposal.identity.IdentityProposalUtils.requireValue;

abstract class AbstractUserProposalHandler implements ProposalHandler<ProposalDraft, ProposalPreparedChange> {
    private final ProposalKind kind;
    private final Set<String> allowedFields;
    protected final UserAdminService userAdminService;
    private final TeamService teamService;

    protected AbstractUserProposalHandler(ProposalKind kind, Set<String> allowedFields,
                                          UserAdminService userAdminService, TeamService teamService) {
        this.kind = kind;
        this.allowedFields = allowedFields;
        this.userAdminService = userAdminService;
        this.teamService = teamService;
    }

    @Override
    public String getEntityType() {
        return kind.name();
    }

    @Override
    public void authorize(ProposalDraft draft, AppUser actor) {
        teamService.requireAdmin(actor);
        userAdminService.getUser(requireValue(draft.userId(), "User ID"), actor);
        if (kind == ProposalKind.USER_ACCESS && draft.fields() != null
                && draft.fields().containsKey("globalRole") && !AppRoles.isSuperAdmin(actor.getGlobalRole())) {
            throw createResponseStatusException(HttpStatus.FORBIDDEN, "Superadmin access is required to update global role");
        }
    }

    @Override
    public ProposalPreparedChange prepare(ProposalDraft draft, AppUser actor) {
        if (!"UPDATE".equals(draft.action())) throw createBadRequestException("User proposals support UPDATE only");
        Map<String, String> requested = extractFields(draft, allowedFields);
        UserResponse current = userAdminService.getUser(draft.userId(), actor);
        Map<String, String> before = createIdentityMap("userId", current.id(), "username", current.username());
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
        for (String key : List.of("username", "timezone", "status", "globalRole")) requireValue(after.get(key), key);
        UpdateUserRequest request = buildUserUpdateRequest(draft, after);
        userAdminService.validateUpdate(draft.userId(), request, actor);
        after.put("username", request.getUsername());
        after.put("email", request.getEmail());
        after.put("timezone", request.getTimezone());
        after.put("status", request.getStatus());
        if (requested.containsKey("globalRole"))
            after.put("globalRole", request.getGlobalRole().trim().toUpperCase(Locale.ROOT));
        return new ProposalPreparedChange(before, after);
    }

    @Override
    public void apply(ProposalDraft draft, ProposalPreparedChange prepared, AppUser actor) {
        userAdminService.updateUserIfUnchanged(draft.userId(), buildUserUpdateRequest(draft, prepared.after()),
                parseTimestamp(prepared.before().get("updatedAt")), actor);
    }

    @Override
    public ProposalDraft buildRevert(ProposalChange appliedChange, AppUser actor) {
        if (!"UPDATE".equals(appliedChange.getOperation())) {
            throw createBadRequestException("This user proposal cannot be reverted");
        }
        ProposalDraft original = JsonUtils.fromJson(appliedChange.getPayload(), ProposalDraft.class);
        Map<String, String> before = parseSnapshot(appliedChange.getBeforeSnapshot());
        Map<String, String> after = parseSnapshot(appliedChange.getAfterSnapshot());
        UserResponse current = userAdminService.getUser(original.userId(), actor);
        Map<String, String> currentValues = createCurrentValues(current);
        Map<String, String> restoreFields = new LinkedHashMap<>();
        for (String field : original.fields().keySet()) {
            if (!Objects.equals(before.get(field), after.get(field))) {
                requireCurrentValue(field, currentValues.get(field), after.get(field));
                restoreFields.put(field, before.get(field));
            }
        }
        if (restoreFields.isEmpty()) {
            throw createBadRequestException("This user proposal has no reversible changes");
        }
        return new ProposalDraft("UPDATE", null, original.userId(), null, null, null, null, restoreFields);
    }

    private Map<String, String> createCurrentValues(UserResponse current) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("username", current.username());
        values.put("email", current.email());
        values.put("displayName", current.displayName());
        values.put("title", current.title());
        values.put("bio", current.bio());
        values.put("timezone", current.timezone());
        values.put("status", current.status());
        values.put("globalRole", current.globalRole());
        return values;
    }
}
