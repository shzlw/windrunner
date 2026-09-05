package com.windrunner.server.identity;

import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.chat.persistence.ChatMessageRepository;
import com.windrunner.server.chat.persistence.ChatSessionRepository;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.project.*;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.proposal.ProposalPreparedChange;
import com.windrunner.server.team.*;
import com.windrunner.server.team.api.*;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.persistence.*;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.user.UserAdminService;
import com.windrunner.server.user.api.*;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IdentityProposalService {
    public enum Kind { TEAM, TEAM_MEMBERSHIP, PROJECT_MEMBERSHIP, USER_PROFILE, USER_ACCESS }
    public record Draft(String action, String teamId, String userId, String projectId, String subjectType,
                        String role, List<String> ownerUserIds, Map<String, String> fields) { }
    public record ChangeView(String id, Kind kind, String action, String status,
                             Map<String, String> before, Map<String, String> after) { }
    /** The first-change fields remain for API compatibility; changes contains the complete batch. */
    public record View(String id, String sourceMessageId, String workflowType, Kind kind, String action, String status,
                       Map<String, String> before, Map<String, String> after, List<ChangeView> changes) { }
    public record Created(String id, Kind kind, String action, String status, List<String> changedFields) { }
    public record Page(List<View> items, boolean hasMore, int offset, int limit) { }
    private record Prepared(Map<String, String> before, Map<String, String> after) { }
    private record Pending(Draft draft, Prepared prepared) { }
    private static final Set<String> PROFILE_FIELDS = Set.of("username", "email", "displayName", "title", "bio", "timezone");

    private final IdentityProposalRepository proposals;
    private final IdentityProposalChangeRepository changes;
    private final AuthService auth;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final EntityIdGenerator ids;
    private final TeamService teams;
    private final UserAdminService users;
    private final ProjectMembershipService memberships;
    private final ProjectAccessService access;
    private final TeamMemberRepository teamMembers;
    private final ProjectMemberRepository projectMembers;
    private final ProjectTeamRepository projectTeams;
    private final ProjectRepository projects;
    private final AppUserRepository userRepository;
    private final ToolAuthorizationService authorization;
    private final IdentityProposalWorkflow workflow;

    @Transactional
    public List<Created> create(ToolExecutionContext context, String sourceMessageId, Kind kind, List<Draft> drafts) {
        if (context == null) throw error(HttpStatus.UNAUTHORIZED, "Tool context required");
        AppUser actor = requireSession(context.chatSessionId(), activeActor(context));
        var message = messages.findByIdAndSessionId(sourceMessageId, context.chatSessionId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Source message not found"));
        if (!"user".equals(message.getRole())) throw bad("Proposal source must be a user message");
        if (kind == null || drafts == null || drafts.isEmpty() || drafts.size() > 25) throw bad("Provide between 1 and 25 changes");
        List<Pending> pending = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        for (Draft draft : drafts) {
            if (draft == null) throw bad("Change required");
            if (kind == Kind.PROJECT_MEMBERSHIP) authorization.requireProjectOwner(context, draft.projectId());
            authorize(kind, draft, actor);
            Prepared prepared = prepare(kind, draft, actor);
            String key = kind + ":" + draft.teamId() + ":" + draft.userId() + ":" + draft.projectId()
                    + ":" + ("ADD".equals(draft.action()) && kind == Kind.TEAM ? prepared.after().get("name") : "");
            if (!targets.add(key)) throw bad("Only one change per target is allowed in a batch");
            if (prepared.before().equals(prepared.after())) throw bad("The requested values are already set");
            pending.add(new Pending(draft, prepared));
        }
        // Mockito-based callers from the pre-batch API may not provide the child repository. Keep
        // their one-row persistence contract while all application wiring uses the generic model.
        if (changes == null) {
            List<Created> legacy = new ArrayList<>();
            for (Pending item : pending) {
                String id = ids.generate(EntityIdType.IDENTITY_CHANGE_PROPOSAL);
                proposals.insert(id, context.chatSessionId(), sourceMessageId, actor.getId(), kind.name(),
                        JsonUtils.toJson(item.draft()), JsonUtils.toJson(item.prepared().before()), JsonUtils.toJson(item.prepared().after()));
                legacy.add(new Created(id, kind, item.draft().action(), "PENDING", item.prepared().after().keySet().stream()
                        .filter(field -> !Objects.equals(item.prepared().before().get(field), item.prepared().after().get(field))).toList()));
            }
            return legacy;
        }
        String parentId = ids.generate(EntityIdType.IDENTITY_CHANGE_PROPOSAL);
        // The kind argument is retained for source compatibility; workflow_type is deliberately generic.
        proposals.insertParent(parentId, workflow == null ? "IDENTITY" : workflow.workflowType(),
                context.chatSessionId(), sourceMessageId, actor.getId());
        List<String> changedFields = new ArrayList<>();
        for (int index = 0; index < pending.size(); index++) {
            Pending item = pending.get(index);
            String changeId = ids.generate(EntityIdType.IDENTITY_PROPOSAL_CHANGE);
            changes.insert(changeId, parentId, index, kind.name(), operation(item.draft().action()),
                    JsonUtils.toJson(targetRef(item.draft(), item.prepared())), JsonUtils.toJson(item.draft()),
                    JsonUtils.toJson(item.prepared().before()), JsonUtils.toJson(item.prepared().after()),
                    JsonUtils.toJson(revisions(item.prepared().before())));
            item.prepared().after().keySet().stream()
                    .filter(field -> !Objects.equals(item.prepared().before().get(field), item.prepared().after().get(field)))
                    .forEach(field -> { if (!changedFields.contains(field)) changedFields.add(field); });
        }
        String action = pending.size() == 1 ? pending.getFirst().draft().action() : "BATCH";
        return List.of(new Created(parentId, pending.size() == 1 ? kind : null, action, "PENDING", changedFields));
    }

    public Page list(String sessionId, AppUser requestedActor, int requestedLimit, int requestedOffset) {
        AppUser actor = requireSession(sessionId, requestedActor);
        int limit = Math.max(1, Math.min(100, requestedLimit));
        int offset = Math.max(0, requestedOffset);
        List<IdentityProposal> page = proposals.page(sessionId, actor.getId(), limit + 1, offset);
        List<View> visible = new ArrayList<>();
        for (IdentityProposal proposal : page.stream().limit(limit).toList()) {
            List<IdentityProposalChange> proposalChanges = changes == null ? List.of() : changes.findByProposalId(proposal.getId());
            if (proposalChanges.isEmpty() && proposal.getDraftJson() != null) {
                try {
                    Kind kind = Kind.valueOf(proposal.getKind());
                    authorize(kind, readDraft(proposal), actor);
                    visible.add(legacyView(proposal));
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() != 403 && e.getStatusCode().value() != 404) throw e;
                }
                continue;
            }
            try {
                List<Draft> drafts = proposalChanges.stream().map(this::readDraft).toList();
                for (int index = 0; index < proposalChanges.size(); index++) authorize(Kind.valueOf(proposalChanges.get(index).getEntityType()), drafts.get(index), actor);
                if (!proposalChanges.isEmpty()) visible.add(view(proposal, proposalChanges));
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() != 403 && e.getStatusCode().value() != 404) throw e;
            }
        }
        return new Page(visible, page.size() > limit, offset, limit);
    }

    @Transactional
    public View decide(String sessionId, String id, String decision, AppUser requestedActor) {
        AppUser actor = requireSession(sessionId, requestedActor);
        IdentityProposal proposal = proposals.findForDecision(id, sessionId, actor.getId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Proposal not found"));
        if (!"PENDING".equals(proposal.getStatus())) throw error(HttpStatus.CONFLICT, "Proposal already decided");
        if (!"ACCEPT".equals(decision) && !"REJECT".equals(decision)) throw bad("Decision must be ACCEPT or REJECT");
        List<IdentityProposalChange> proposalChanges = changes == null ? List.of() : changes.findByProposalId(id);
        if (proposalChanges.isEmpty() && proposal.getDraftJson() != null) return decideLegacy(proposal, sessionId, decision, actor);
        if (proposalChanges.isEmpty()) throw error(HttpStatus.CONFLICT, "Proposal has no changes");
        List<Draft> drafts = proposalChanges.stream().map(this::readDraft).toList();
        for (int index = 0; index < proposalChanges.size(); index++) authorize(Kind.valueOf(proposalChanges.get(index).getEntityType()), drafts.get(index), actor);
        if (proposals.claimForDecision(id, sessionId, actor.getId()) != 1) throw error(HttpStatus.CONFLICT, "Proposal already decided");
        if ("ACCEPT".equals(decision)) {
            actor = auth.requireActiveActor(actor);
            Set<String> advancedRevisionKeys = new HashSet<>();
            for (int index = 0; index < proposalChanges.size(); index++) {
                IdentityProposalChange change = proposalChanges.get(index);
                Kind kind = Kind.valueOf(change.getEntityType());
                Draft draft = drafts.get(index);
                authorize(kind, draft, actor);
                Map<String, String> storedBefore = readMap(change.getBeforeSnapshot());
                Prepared current = prepare(kind, draft, actor);
                String revisionKey = revisionKey(kind, draft);
                boolean sameBatchRevision = revisionKey != null && advancedRevisionKeys.contains(revisionKey);
                Map<String, String> expected = current.before();
                Map<String, String> storedAfter = readMap(change.getAfterSnapshot());
                boolean beforeMatches = sameBatchRevision
                        ? sameExceptUpdatedAt(current.before(), storedBefore)
                        : current.before().equals(storedBefore);
                boolean afterMatches = sameBatchRevision
                        ? sameExceptUpdatedAt(current.after(), storedAfter)
                        : current.after().equals(storedAfter);
                if (!beforeMatches || !afterMatches) throw error(HttpStatus.CONFLICT, "This target changed after the proposal was created. Ask AI to review it again.");
                apply(kind, draft, expected, current.after(), actor);
                if (revisionKey != null) advancedRevisionKeys.add(revisionKey);
                // A batch may change the actor's own role or status. Re-load it before
                // authorizing the next child so one stale privilege cannot authorize
                // the remainder of the batch.
                actor = auth.requireActiveActor(actor);
            }
            for (IdentityProposalChange change : proposalChanges) if (changes.decide(change.getId(), id, "APPLIED", null) != 1) throw error(HttpStatus.CONFLICT, "Proposal change was already decided");
            proposal.setStatus("APPLIED");
        } else {
            for (IdentityProposalChange change : proposalChanges) if (changes.decide(change.getId(), id, "REJECTED", null) != 1) throw error(HttpStatus.CONFLICT, "Proposal change was already decided");
            proposal.setStatus("REJECTED");
        }
        if (proposals.decide(id, sessionId, actor.getId(), proposal.getStatus()) != 1) throw error(HttpStatus.CONFLICT, "Proposal already decided");
        return view(proposal, proposalChanges);
    }

    private View decideLegacy(IdentityProposal proposal, String sessionId, String decision, AppUser actor) {
        Kind kind = Kind.valueOf(proposal.getKind()); Draft draft = readDraft(proposal); authorize(kind, draft, actor);
        if (proposals.claimForDecision(proposal.getId(), sessionId, actor.getId()) != 1) throw error(HttpStatus.CONFLICT, "Proposal already decided");
        if ("ACCEPT".equals(decision)) {
            actor = auth.requireActiveActor(actor); authorize(kind, draft, actor);
            Map<String, String> expected = readMap(proposal.getBeforeJson()); Prepared current = prepare(kind, draft, actor);
            if (!current.before().equals(expected) || !current.after().equals(readMap(proposal.getAfterJson()))) throw error(HttpStatus.CONFLICT, "This target changed after the proposal was created. Ask AI to review it again.");
            apply(kind, draft, expected, current.after(), actor); proposal.setStatus("APPLIED");
        } else proposal.setStatus("REJECTED");
        if (proposals.decide(proposal.getId(), sessionId, actor.getId(), proposal.getStatus()) != 1) throw error(HttpStatus.CONFLICT, "Proposal already decided");
        return legacyView(proposal);
    }

    private AppUser requireSession(String sessionId, AppUser requestedActor) {
        AppUser actor = auth.requireActiveActor(requestedActor);
        if (sessionId == null || sessions.findByIdAndUserId(sessionId, actor.getId()).isEmpty()) throw error(HttpStatus.NOT_FOUND, "Chat session not found");
        return actor;
    }
    private AppUser activeActor(ToolExecutionContext context) { return authorization.requireActor(context); }
    private void authorize(Kind kind, Draft draft, AppUser actor) {
        if (workflow != null) {
            workflow.handler(kind.name()).authorize(draft, actor);
            return;
        }
        authorizeLegacy(kind, draft, actor);
    }

    private void authorizeLegacy(Kind kind, Draft draft, AppUser actor) {
        if (kind == Kind.PROJECT_MEMBERSHIP) access.requireProjectRole(required(draft.projectId(), "Project ID"), actor, ProjectRoles.OWNER);
        else {
            teams.requireAdmin(actor);
            if (kind == Kind.USER_PROFILE || kind == Kind.USER_ACCESS) {
                users.getUser(required(draft.userId(), "User ID"), actor);
                if (kind == Kind.USER_ACCESS && draft.fields() != null && draft.fields().containsKey("globalRole") && !AppRoles.isSuperAdmin(actor.getGlobalRole())) throw error(HttpStatus.FORBIDDEN, "Superadmin access is required to update global role");
            }
        }
    }
    private Prepared prepare(Kind kind, Draft draft, AppUser actor) {
        if (!Set.of("ADD", "UPDATE", "REMOVE").contains(Objects.toString(draft.action(), ""))) throw bad("Unsupported action");
        if (workflow != null) {
            ProposalPreparedChange prepared = workflow.handler(kind.name()).prepare(draft, actor);
            return new Prepared(prepared.before(), prepared.after());
        }
        return prepareLegacy(kind, draft, actor);
    }
    private Prepared prepareLegacy(Kind kind, Draft draft, AppUser actor) {
        return switch (kind) { case TEAM -> prepareTeam(draft, actor); case TEAM_MEMBERSHIP -> prepareTeamMembership(draft); case PROJECT_MEMBERSHIP -> prepareProjectMembership(draft); case USER_PROFILE, USER_ACCESS -> prepareUser(kind, draft, actor); };
    }
    private Prepared prepareTeam(Draft d, AppUser actor) {
        if ("REMOVE".equals(d.action())) throw bad("Team deletion is not supported by this tool");
        if ("ADD".equals(d.action()) && d.teamId() != null) throw bad("ADD must not supply an existing teamId");
        if ("UPDATE".equals(d.action()) && d.ownerUserIds() != null) throw bad("Use team membership proposals to change owners");
        Map<String, String> fields = fields(d, Set.of("name", "description")); Map<String, String> before = new LinkedHashMap<>(); Team team = new Team(); String id = null;
        if ("UPDATE".equals(d.action())) { id = required(d.teamId(), "Team ID"); Team current = teams.getTeam(id); before.put("teamId", id); before.put("name", current.getName()); before.put("description", current.getDescription()); putRevision(before, "updatedAt", current.getUpdatedAt()); team.setName(current.getName()); team.setDescription(current.getDescription()); }
        if (fields.containsKey("name")) team.setName(required(fields.get("name"), "Name")); if (fields.containsKey("description")) team.setDescription(fields.get("description")); teams.validateTeamChange(id, team, d.ownerUserIds(), actor);
        Map<String, String> after = new LinkedHashMap<>(before); after.put("name", team.getName()); after.put("description", team.getDescription());
        if (id == null) { List<String> owners = d.ownerUserIds() == null ? List.of() : d.ownerUserIds(); if (owners.size() > 25) throw bad("At most 25 initial owners are allowed"); after.put("ownerUserIds", String.join(", ", owners)); List<String> ownerNames = new ArrayList<>(); userRepository.findAllById(owners).forEach(user -> ownerNames.add(display(user))); Collections.sort(ownerNames); after.put("ownerNames", String.join(", ", ownerNames)); }
        return new Prepared(before, after);
    }
    private Prepared prepareTeamMembership(Draft d) {
        String teamId = required(d.teamId(), "Team ID"), userId = required(d.userId(), "User ID"); Team team = teams.getTeam(teamId); AppUser user = memberUser(userId); var existing = teamMembers.findByTeamIdAndUserId(teamId, userId).orElse(null); String oldRole = existing == null ? null : existing.getRole(); membershipAction(d.action(), oldRole); String newRole = "REMOVE".equals(d.action()) ? null : teamRole(d.role());
        if (TeamRoles.TEAM_OWNER.equals(oldRole) && !TeamRoles.TEAM_OWNER.equals(newRole) && teamMembers.countOwners(teamId) <= 1) throw bad("At least one team owner is required");
        Map<String, String> before = identity("teamId", teamId, "team", team.getName(), "userId", userId, "user", display(user)); before.put("role", oldRole); putRevision(before, "updatedAt", team.getUpdatedAt()); putRevision(before, "membershipUpdatedAt", existing == null ? null : existing.getUpdatedAt()); Map<String, String> after = new LinkedHashMap<>(before); after.put("role", newRole); return new Prepared(before, after);
    }
    private Prepared prepareProjectMembership(Draft d) {
        String projectId = required(d.projectId(), "Project ID"); var project = projects.findById(projectId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Project not found")); String subjectType = required(d.subjectType(), "Subject type"); if (IdentityProposalSupport.present(d.userId()) == IdentityProposalSupport.present(d.teamId())) throw bad("Supply exactly one userId or teamId"); String oldRole; Map<String, String> before = identity("projectId", projectId, "project", project.getName(), "subjectType", subjectType);
        if ("USER".equals(subjectType)) { String id = required(d.userId(), "User ID"); before.put("userId", id); before.put("user", display(memberUser(id))); oldRole = projectMembers.findByProjectIdAndUserId(projectId, id).map(m -> m.getRole()).orElse(null); }
        else if ("TEAM".equals(subjectType)) { String id = required(d.teamId(), "Team ID"); before.put("teamId", id); before.put("team", teams.getTeam(id).getName()); oldRole = projectTeams.findByProjectIdAndTeamId(projectId, id).map(m -> m.getRole()).orElse(null); }
        else throw bad("Subject type must be USER or TEAM");
        membershipAction(d.action(), oldRole); String newRole = "REMOVE".equals(d.action()) ? null : projectRole(d.role()); access.requireAnotherOwnerBeforeRemovingOwner(projectId, ProjectRoles.OWNER.equals(oldRole) && !ProjectRoles.OWNER.equals(newRole)); before.put("role", oldRole); putRevision(before, "updatedAt", project.getUpdatedAt());
        if ("USER".equals(subjectType)) projectMembers.findByProjectIdAndUserId(projectId, required(d.userId(), "User ID")).ifPresent(member -> putRevision(before, "membershipUpdatedAt", member.getUpdatedAt())); else projectTeams.findByProjectIdAndTeamId(projectId, required(d.teamId(), "Team ID")).ifPresent(member -> putRevision(before, "membershipUpdatedAt", member.getUpdatedAt()));
        Map<String, String> after = new LinkedHashMap<>(before); after.put("role", newRole); return new Prepared(before, after);
    }
    private Prepared prepareUser(Kind kind, Draft d, AppUser actor) {
        if (!"UPDATE".equals(d.action())) throw bad("User proposals support UPDATE only"); Map<String, String> requested = fields(d, kind == Kind.USER_PROFILE ? PROFILE_FIELDS : Set.of("status", "globalRole")); UserResponse current = users.getUser(d.userId(), actor); Map<String, String> before = identity("userId", current.id(), "username", current.username()); before.put("email", current.email()); before.put("displayName", current.displayName()); before.put("title", current.title()); before.put("bio", current.bio()); before.put("timezone", current.timezone()); before.put("status", current.status()); before.put("globalRole", current.globalRole()); putRevision(before, "updatedAt", current.updatedAt()); Map<String, String> after = new LinkedHashMap<>(before); requested.forEach((key, value) -> after.put(key, value == null || value.isBlank() ? null : value.trim())); for (String key : List.of("username", "timezone", "status", "globalRole")) required(after.get(key), key); UpdateUserRequest request = userRequest(d, after); users.validateUpdate(d.userId(), request, actor); after.put("username", request.getUsername()); after.put("email", request.getEmail()); after.put("timezone", request.getTimezone()); after.put("status", request.getStatus()); if (requested.containsKey("globalRole")) after.put("globalRole", request.getGlobalRole().trim().toUpperCase(Locale.ROOT)); return new Prepared(before, after);
    }
    private void apply(Kind kind, Draft d, Map<String, String> expected, Map<String, String> after, AppUser actor) {
        if (workflow != null) {
            workflow.handler(kind.name()).apply(d, new ProposalPreparedChange(expected, after), actor);
            return;
        }
        applyLegacy(kind, d, expected, after, actor);
    }

    private void applyLegacy(Kind kind, Draft d, Map<String, String> expected, Map<String, String> after, AppUser actor) {
        switch (kind) {
            case TEAM -> { if ("ADD".equals(d.action())) teams.createTeam(new CreateTeamRequest(after.get("name"), after.get("description"), d.ownerUserIds()), actor); else { Team team = new Team(); team.setName(after.get("name")); team.setDescription(after.get("description")); teams.updateTeamIfUnchanged(d.teamId(), team, timestamp(expected.get("updatedAt")), actor); } }
            case TEAM_MEMBERSHIP -> teams.applyMembershipOptimistic(d.teamId(), d.userId(), after.get("role"), d.action(), timestamp(expected.get("updatedAt")), timestamp(expected.get("membershipUpdatedAt")), actor);
            case PROJECT_MEMBERSHIP -> { if ("USER".equals(d.subjectType())) memberships.applyUserOptimistic(d.projectId(), d.userId(), after.get("role"), d.action(), timestamp(expected.get("updatedAt")), timestamp(expected.get("membershipUpdatedAt")), actor); else memberships.applyTeamOptimistic(d.projectId(), d.teamId(), after.get("role"), d.action(), timestamp(expected.get("updatedAt")), timestamp(expected.get("membershipUpdatedAt")), actor); }
            case USER_PROFILE, USER_ACCESS -> users.updateUserIfUnchanged(d.userId(), userRequest(d, after), timestamp(expected.get("updatedAt")), actor);
        }
    }
    private Map<String, String> targetRef(Draft d, Prepared prepared) { Map<String, String> target = new LinkedHashMap<>(); if (d.teamId() != null) target.put("teamId", d.teamId()); if (d.userId() != null) target.put("userId", d.userId()); if (d.projectId() != null) target.put("projectId", d.projectId()); if (d.subjectType() != null) target.put("subjectType", d.subjectType()); if (target.isEmpty() && prepared.after().get("name") != null) target.put("name", prepared.after().get("name")); return target; }
    private Map<String, String> revisions(Map<String, String> values) { Map<String, String> result = new LinkedHashMap<>(); for (String key : List.of("updatedAt", "membershipUpdatedAt")) if (values.containsKey(key)) result.put(key, values.get(key)); return result; }
    private UpdateUserRequest userRequest(Draft d, Map<String, String> values) { UpdateUserRequest request = new UpdateUserRequest(); request.setUsername(values.get("username")); request.setEmail(values.get("email")); request.setDisplayName(values.get("displayName")); request.setTitle(values.get("title")); request.setBio(values.get("bio")); request.setTimezone(values.get("timezone")); request.setStatus(values.get("status")); if (d.fields() != null && d.fields().containsKey("globalRole")) request.setGlobalRole(values.get("globalRole")); return request; }
    private Map<String, String> fields(Draft d, Set<String> allowed) { if (d.fields() == null || d.fields().isEmpty() || !allowed.containsAll(d.fields().keySet())) throw bad("Unsupported or empty fields"); if (d.fields().values().stream().anyMatch(v -> v != null && v.length() > 4000)) throw bad("Field values must be 4000 characters or fewer"); return d.fields(); }
    private AppUser memberUser(String id) { AppUser user = userRepository.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "User not found")); if (AppRoles.isSuperAdmin(user.getGlobalRole())) throw bad("Super admin users cannot be members"); return user; }
    private void membershipAction(String action, String oldRole) { if ("ADD".equals(action) && oldRole != null) throw error(HttpStatus.CONFLICT, "Membership already exists; use UPDATE with these exact IDs"); if (!"ADD".equals(action) && oldRole == null) throw error(HttpStatus.NOT_FOUND, "Membership not found"); }
    private String teamRole(String role) { try { return TeamRoles.normalize(required(role, "Role")); } catch (IllegalArgumentException e) { throw bad(e.getMessage()); } }
    private String projectRole(String role) { try { return ProjectRoles.normalize(required(role, "Role")); } catch (IllegalArgumentException e) { throw bad(e.getMessage()); } }
    private String display(AppUser user) { return user.getDisplayName() == null ? user.getUsername() : user.getDisplayName(); }
    private Map<String, String> identity(String... pairs) { Map<String, String> result = new LinkedHashMap<>(); for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]); return result; }
    private Draft readDraft(IdentityProposal proposal) { return JsonUtils.fromJson(proposal.getDraftJson(), Draft.class); }
    private Draft readDraft(IdentityProposalChange change) { return JsonUtils.fromJson(change.getPayload(), Draft.class); }
    private void putRevision(Map<String, String> values, String key, OffsetDateTime value) { if (value != null) values.put(key, value.toString()); }
    private OffsetDateTime timestamp(String value) { return value == null || value.isBlank() ? null : OffsetDateTime.parse(value); }
    @SuppressWarnings("unchecked") private Map<String, String> readMap(String json) { return JsonUtils.fromJson(json, LinkedHashMap.class); }
    private Map<String, String> readMapOrEmpty(String json) { return json == null ? new LinkedHashMap<>() : readMap(json); }
    private String operation(String action) { return switch (action) { case "ADD" -> "CREATE"; case "REMOVE" -> "DELETE"; default -> action; }; }
    private String action(String operation) { return switch (operation) { case "CREATE" -> "ADD"; case "DELETE" -> "REMOVE"; default -> operation; }; }
    private String revisionKey(Kind kind, Draft draft) {
        return switch (kind) {
            case TEAM_MEMBERSHIP -> "TEAM:" + draft.teamId();
            case PROJECT_MEMBERSHIP -> "PROJECT:" + draft.projectId();
            default -> null;
        };
    }
    private boolean sameExceptUpdatedAt(Map<String, String> left, Map<String, String> right) {
        Map<String, String> leftComparable = new LinkedHashMap<>(left);
        Map<String, String> rightComparable = new LinkedHashMap<>(right);
        leftComparable.remove("updatedAt");
        rightComparable.remove("updatedAt");
        return leftComparable.equals(rightComparable);
    }
    private View view(IdentityProposal proposal, List<IdentityProposalChange> proposalChanges) { List<ChangeView> views = proposalChanges.stream().map(change -> new ChangeView(change.getId(), Kind.valueOf(change.getEntityType()), action(change.getOperation()), change.getStatus(), readMapOrEmpty(change.getBeforeSnapshot()), readMapOrEmpty(change.getAfterSnapshot()))).toList(); ChangeView first = views.getFirst(); return new View(proposal.getId(), proposal.getSourceMessageId(), proposal.getWorkflowType(), first.kind(), proposalChanges.size() == 1 ? first.action() : "BATCH", proposal.getStatus(), first.before(), first.after(), views); }
    private View legacyView(IdentityProposal proposal) { Draft draft = readDraft(proposal); ChangeView change = new ChangeView(proposal.getId(), Kind.valueOf(proposal.getKind()), draft.action(), proposal.getStatus(), readMap(proposal.getBeforeJson()), readMap(proposal.getAfterJson())); return new View(proposal.getId(), proposal.getSourceMessageId(), proposal.getWorkflowType(), change.kind(), change.action(), change.status(), change.before(), change.after(), List.of(change)); }
    private String required(String value, String label) { if (value == null || value.isBlank()) throw bad(label + " is required"); if (!value.equals(value.trim())) throw bad(label + " must not contain surrounding whitespace"); return value; }
    private static ResponseStatusException bad(String message) { return error(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
