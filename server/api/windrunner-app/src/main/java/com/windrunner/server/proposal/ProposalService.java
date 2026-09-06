package com.windrunner.server.proposal;

import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.chat.persistence.ChatMessageRepository;
import com.windrunner.server.chat.persistence.ChatSessionRepository;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectMembershipService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.team.TeamRoles;
import com.windrunner.server.team.TeamService;
import com.windrunner.server.team.api.CreateTeamRequest;
import com.windrunner.server.team.domain.Team;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.user.UserAdminService;
import com.windrunner.server.user.api.UpdateUserRequest;
import com.windrunner.server.user.api.UserResponse;
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
public class ProposalService {
    private record Prepared(Map<String, String> before, Map<String, String> after) {
    }

    private record Pending(ProposalDraft draft, Prepared prepared) {
    }

    private static final Set<String> PROFILE_FIELDS = Set.of("username", "email", "displayName", "title", "bio", "timezone");

    private final ProposalRepository proposalRepository;
    private final ProposalChangeRepository proposalChangeRepository;
    private final AuthService authService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final EntityIdGenerator entityIdGenerator;
    private final TeamService teamService;
    private final UserAdminService userAdminService;
    private final ProjectMembershipService projectMembershipService;
    private final ProjectAccessService projectAccessService;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final ProjectRepository projectRepository;
    private final AppUserRepository appUserRepository;
    private final ToolAuthorizationService toolAuthorizationService;
    /**
     * The active workflow supplies entity-specific authorization, snapshots, and application.
     * The null fallback is retained only for legacy one-change rows and isolated callers.
     */
    private final ProposalWorkflow<ProposalDraft> proposalWorkflow;

    @Transactional
    public List<ProposalCreated> create(ToolExecutionContext context, String sourceMessageId, ProposalKind kind, List<ProposalDraft> drafts) {
        if (context == null) throw createResponseStatusException(HttpStatus.UNAUTHORIZED, "Tool context required");
        AppUser actor = requireSession(context.chatSessionId(), resolveActiveActor(context));
        var message = chatMessageRepository.findByIdAndSessionId(sourceMessageId, context.chatSessionId())
                .orElseThrow(() -> createResponseStatusException(HttpStatus.NOT_FOUND, "Source message not found"));
        if (!"user".equals(message.getRole())) throw createBadRequestException("Proposal source must be a user message");
        if (kind == null || drafts == null || drafts.isEmpty() || drafts.size() > 25)
            throw createBadRequestException("Provide between 1 and 25 changes");
        List<Pending> pending = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        for (ProposalDraft draft : drafts) {
            if (draft == null) throw createBadRequestException("Change required");
            if (kind == ProposalKind.PROJECT_MEMBERSHIP) toolAuthorizationService.requireProjectOwner(context, draft.projectId());
            authorize(kind, draft, actor);
            Prepared prepared = prepare(kind, draft, actor);
            String key = kind + ":" + draft.teamId() + ":" + draft.userId() + ":" + draft.projectId()
                    + ":" + ("ADD".equals(draft.action()) && kind == ProposalKind.TEAM ? prepared.after().get("name") : "");
            if (!targets.add(key)) throw createBadRequestException("Only one change per target is allowed in a batch");
            if (prepared.before().equals(prepared.after())) throw createBadRequestException("The requested values are already set");
            pending.add(new Pending(draft, prepared));
        }
        // Mockito-based callers from the pre-batch API may not provide the child repository. Keep
        // their one-row persistence contract while all application wiring uses the generic model.
        if (proposalChangeRepository == null) {
            List<ProposalCreated> legacy = new ArrayList<>();
            for (Pending item : pending) {
                String id = entityIdGenerator.generate(EntityIdType.PROPOSAL);
                proposalRepository.insert(id, getWorkflowType(), context.chatSessionId(), sourceMessageId, actor.getId(), kind.name(),
                        JsonUtils.toJson(item.draft()), JsonUtils.toJson(item.prepared().before()), JsonUtils.toJson(item.prepared().after()));
                legacy.add(new ProposalCreated(id, kind, item.draft().action(), "PENDING", item.prepared().after().keySet().stream()
                        .filter(field -> !Objects.equals(item.prepared().before().get(field), item.prepared().after().get(field))).toList()));
            }
            return legacy;
        }
        String parentId = entityIdGenerator.generate(EntityIdType.PROPOSAL);
        // The kind argument is retained for source compatibility; workflow_type is deliberately generic.
        proposalRepository.insertParent(parentId, getWorkflowType(),
                context.chatSessionId(), sourceMessageId, actor.getId());
        List<String> changedFields = new ArrayList<>();
        for (int index = 0; index < pending.size(); index++) {
            Pending item = pending.get(index);
            String changeId = entityIdGenerator.generate(EntityIdType.PROPOSAL_CHANGE);
            proposalChangeRepository.insert(changeId, parentId, index, kind.name(), toAuditOperation(item.draft().action()),
                    JsonUtils.toJson(buildTargetReference(item.draft(), item.prepared())), JsonUtils.toJson(item.draft()),
                    JsonUtils.toJson(item.prepared().before()), JsonUtils.toJson(item.prepared().after()),
                    JsonUtils.toJson(collectRevisions(item.prepared().before())));
            item.prepared().after().keySet().stream()
                    .filter(field -> !Objects.equals(item.prepared().before().get(field), item.prepared().after().get(field)))
                    .forEach(field -> {
                        if (!changedFields.contains(field)) changedFields.add(field);
                    });
        }
        String action = pending.size() == 1 ? pending.getFirst().draft().action() : "BATCH";
        return List.of(new ProposalCreated(parentId, pending.size() == 1 ? kind : null, action, "PENDING", changedFields));
    }

    public ProposalPage list(String sessionId, AppUser requestedActor, int requestedLimit, int requestedOffset) {
        AppUser actor = requireSession(sessionId, requestedActor);
        int limit = Math.max(1, Math.min(100, requestedLimit));
        int offset = Math.max(0, requestedOffset);
        List<Proposal> page = proposalRepository.page(getWorkflowType(), sessionId, actor.getId(), limit + 1, offset);
        List<ProposalView> visible = new ArrayList<>();
        for (Proposal proposal : page.stream().limit(limit).toList()) {
            List<ProposalChange> proposalChanges = proposalChangeRepository == null ? List.of() : proposalChangeRepository.findByProposalId(proposal.getId());
            if (proposalChanges.isEmpty() && proposal.getDraftJson() != null) {
                try {
                    ProposalKind kind = ProposalKind.valueOf(proposal.getKind());
                    authorize(kind, readDraft(proposal), actor);
                    visible.add(createLegacyView(proposal));
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() != 403 && e.getStatusCode().value() != 404) throw e;
                }
                continue;
            }
            try {
                List<ProposalDraft> drafts = proposalChanges.stream().map(this::readDraft).toList();
                for (int index = 0; index < proposalChanges.size(); index++)
                    authorize(ProposalKind.valueOf(proposalChanges.get(index).getEntityType()), drafts.get(index), actor);
                if (!proposalChanges.isEmpty()) visible.add(createView(proposal, proposalChanges));
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() != 403 && e.getStatusCode().value() != 404) throw e;
            }
        }
        return new ProposalPage(visible, page.size() > limit, offset, limit);
    }

    @Transactional
    public ProposalView decide(String sessionId, String id, String decision, AppUser requestedActor) {
        AppUser actor = requireSession(sessionId, requestedActor);
        Proposal proposal = proposalRepository.findForDecision(getWorkflowType(), id, sessionId, actor.getId()).orElseThrow(() -> createResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
        if (!"PENDING".equals(proposal.getStatus())) throw createResponseStatusException(HttpStatus.CONFLICT, "Proposal already decided");
        if (!"ACCEPT".equals(decision) && !"REJECT".equals(decision)) throw createBadRequestException("Decision must be ACCEPT or REJECT");
        List<ProposalChange> proposalChanges = proposalChangeRepository == null ? List.of() : proposalChangeRepository.findByProposalId(id);
        if (proposalChanges.isEmpty() && proposal.getDraftJson() != null)
            return decideLegacy(proposal, sessionId, decision, actor);
        if (proposalChanges.isEmpty()) throw createResponseStatusException(HttpStatus.CONFLICT, "Proposal has no changes");
        List<ProposalDraft> drafts = proposalChanges.stream().map(this::readDraft).toList();
        for (int index = 0; index < proposalChanges.size(); index++)
            authorize(ProposalKind.valueOf(proposalChanges.get(index).getEntityType()), drafts.get(index), actor);
        if (proposalRepository.claimForDecision(id, sessionId, actor.getId()) != 1)
            throw createResponseStatusException(HttpStatus.CONFLICT, "Proposal already decided");
        if ("ACCEPT".equals(decision)) {
            actor = authService.requireActiveActor(actor);
            Set<String> advancedRevisionKeys = new HashSet<>();
            for (int index = 0; index < proposalChanges.size(); index++) {
                ProposalChange change = proposalChanges.get(index);
                ProposalKind kind = ProposalKind.valueOf(change.getEntityType());
                ProposalDraft draft = drafts.get(index);
                authorize(kind, draft, actor);
                Map<String, String> storedBefore = parseMap(change.getBeforeSnapshot());
                Prepared current = prepare(kind, draft, actor);
                String revisionKey = buildRevisionKey(kind, draft);
                boolean sameBatchRevision = revisionKey != null && advancedRevisionKeys.contains(revisionKey);
                Map<String, String> expected = current.before();
                Map<String, String> storedAfter = parseMap(change.getAfterSnapshot());
                boolean beforeMatches = sameBatchRevision
                        ? hasSameValuesExceptUpdatedAt(current.before(), storedBefore)
                        : current.before().equals(storedBefore);
                boolean afterMatches = sameBatchRevision
                        ? hasSameValuesExceptUpdatedAt(current.after(), storedAfter)
                        : current.after().equals(storedAfter);
                if (!beforeMatches || !afterMatches)
                    throw createResponseStatusException(HttpStatus.CONFLICT, "This target changed after the proposal was created. Ask AI to review it again.");
                apply(kind, draft, expected, current.after(), actor);
                if (revisionKey != null) advancedRevisionKeys.add(revisionKey);
                // A batch may change the actor's own role or status. Re-load it before
                // authorizing the next child so one stale privilege cannot authorize
                // the remainder of the batch.
                actor = authService.requireActiveActor(actor);
            }
            for (ProposalChange change : proposalChanges)
                if (proposalChangeRepository.decide(change.getId(), id, "APPLIED", null) != 1)
                    throw createResponseStatusException(HttpStatus.CONFLICT, "Proposal change was already decided");
            proposal.setStatus("APPLIED");
        } else {
            for (ProposalChange change : proposalChanges)
                if (proposalChangeRepository.decide(change.getId(), id, "REJECTED", null) != 1)
                    throw createResponseStatusException(HttpStatus.CONFLICT, "Proposal change was already decided");
            proposal.setStatus("REJECTED");
        }
        if (proposalRepository.decide(id, sessionId, actor.getId(), proposal.getStatus()) != 1)
            throw createResponseStatusException(HttpStatus.CONFLICT, "Proposal already decided");
        return createView(proposal, proposalChanges);
    }

    private ProposalView decideLegacy(Proposal proposal, String sessionId, String decision, AppUser actor) {
        ProposalKind kind = ProposalKind.valueOf(proposal.getKind());
        ProposalDraft draft = readDraft(proposal);
        authorize(kind, draft, actor);
        if (proposalRepository.claimForDecision(proposal.getId(), sessionId, actor.getId()) != 1)
            throw createResponseStatusException(HttpStatus.CONFLICT, "Proposal already decided");
        if ("ACCEPT".equals(decision)) {
            actor = authService.requireActiveActor(actor);
            authorize(kind, draft, actor);
            Map<String, String> expected = parseMap(proposal.getBeforeJson());
            Prepared current = prepare(kind, draft, actor);
            if (!current.before().equals(expected) || !current.after().equals(parseMap(proposal.getAfterJson())))
                throw createResponseStatusException(HttpStatus.CONFLICT, "This target changed after the proposal was created. Ask AI to review it again.");
            apply(kind, draft, expected, current.after(), actor);
            proposal.setStatus("APPLIED");
        } else proposal.setStatus("REJECTED");
        if (proposalRepository.decide(proposal.getId(), sessionId, actor.getId(), proposal.getStatus()) != 1)
            throw createResponseStatusException(HttpStatus.CONFLICT, "Proposal already decided");
        return createLegacyView(proposal);
    }

    private AppUser requireSession(String sessionId, AppUser requestedActor) {
        AppUser actor = authService.requireActiveActor(requestedActor);
        if (sessionId == null || chatSessionRepository.findByIdAndUserId(sessionId, actor.getId()).isEmpty())
            throw createResponseStatusException(HttpStatus.NOT_FOUND, "Chat session not found");
        return actor;
    }

    private AppUser resolveActiveActor(ToolExecutionContext context) {
        return toolAuthorizationService.requireActor(context);
    }

    private String getWorkflowType() {
        return proposalWorkflow == null ? "IDENTITY" : proposalWorkflow.getWorkflowType();
    }

    private void authorize(ProposalKind kind, ProposalDraft draft, AppUser actor) {
        if (proposalWorkflow != null) {
            proposalWorkflow.handler(kind.name()).authorize(draft, actor);
            return;
        }
        authorizeLegacy(kind, draft, actor);
    }

    private void authorizeLegacy(ProposalKind kind, ProposalDraft draft, AppUser actor) {
        if (kind == ProposalKind.PROJECT_MEMBERSHIP)
            projectAccessService.requireProjectRole(requireValue(draft.projectId(), "Project ID"), actor, ProjectRoles.OWNER);
        else {
            teamService.requireAdmin(actor);
            if (kind == ProposalKind.USER_PROFILE || kind == ProposalKind.USER_ACCESS) {
                userAdminService.getUser(requireValue(draft.userId(), "User ID"), actor);
                if (kind == ProposalKind.USER_ACCESS && draft.fields() != null && draft.fields().containsKey("globalRole") && !AppRoles.isSuperAdmin(actor.getGlobalRole()))
                    throw createResponseStatusException(HttpStatus.FORBIDDEN, "Superadmin access is required to update global role");
            }
        }
    }

    private Prepared prepare(ProposalKind kind, ProposalDraft draft, AppUser actor) {
        if (!Set.of("ADD", "UPDATE", "REMOVE").contains(Objects.toString(draft.action(), "")))
            throw createBadRequestException("Unsupported action");
        if (proposalWorkflow != null) {
            ProposalPreparedChange prepared = proposalWorkflow.handler(kind.name()).prepare(draft, actor);
            return new Prepared(prepared.before(), prepared.after());
        }
        return prepareLegacy(kind, draft, actor);
    }

    private Prepared prepareLegacy(ProposalKind kind, ProposalDraft draft, AppUser actor) {
        return switch (kind) {
            case TEAM -> prepareTeam(draft, actor);
            case TEAM_MEMBERSHIP -> prepareTeamMembership(draft);
            case PROJECT_MEMBERSHIP -> prepareProjectMembership(draft);
            case USER_PROFILE, USER_ACCESS -> prepareUser(kind, draft, actor);
        };
    }

    private Prepared prepareTeam(ProposalDraft d, AppUser actor) {
        if ("REMOVE".equals(d.action())) throw createBadRequestException("Team deletion is not supported by this tool");
        if ("ADD".equals(d.action()) && d.teamId() != null) throw createBadRequestException("ADD must not supply an existing teamId");
        if ("UPDATE".equals(d.action()) && d.ownerUserIds() != null)
            throw createBadRequestException("Use team membership proposals to change owners");
        Map<String, String> fields = extractFields(d, Set.of("name", "description"));
        Map<String, String> before = new LinkedHashMap<>();
        Team team = new Team();
        String id = null;
        if ("UPDATE".equals(d.action())) {
            id = requireValue(d.teamId(), "Team ID");
            Team current = teamService.getTeam(id);
            before.put("teamId", id);
            before.put("name", current.getName());
            before.put("description", current.getDescription());
            putRevision(before, "updatedAt", current.getUpdatedAt());
            team.setName(current.getName());
            team.setDescription(current.getDescription());
        }
        if (fields.containsKey("name")) team.setName(requireValue(fields.get("name"), "Name"));
        if (fields.containsKey("description")) team.setDescription(fields.get("description"));
        teamService.validateTeamChange(id, team, d.ownerUserIds(), actor);
        Map<String, String> after = new LinkedHashMap<>(before);
        after.put("name", team.getName());
        after.put("description", team.getDescription());
        if (id == null) {
            List<String> owners = d.ownerUserIds() == null ? List.of() : d.ownerUserIds();
            if (owners.size() > 25) throw createBadRequestException("At most 25 initial owners are allowed");
            after.put("ownerUserIds", String.join(", ", owners));
            List<String> ownerNames = new ArrayList<>();
            appUserRepository.findAllById(owners).forEach(user -> ownerNames.add(getDisplayName(user)));
            Collections.sort(ownerNames);
            after.put("ownerNames", String.join(", ", ownerNames));
        }
        return new Prepared(before, after);
    }

    private Prepared prepareTeamMembership(ProposalDraft d) {
        String teamId = requireValue(d.teamId(), "Team ID"), userId = requireValue(d.userId(), "User ID");
        Team team = teamService.getTeam(teamId);
        AppUser user = requireMemberUser(userId);
        var existing = teamMemberRepository.findByTeamIdAndUserId(teamId, userId).orElse(null);
        String oldRole = existing == null ? null : existing.getRole();
        validateMembershipAction(d.action(), oldRole);
        String newRole = "REMOVE".equals(d.action()) ? null : normalizeTeamRole(d.role());
        if (TeamRoles.TEAM_OWNER.equals(oldRole) && !TeamRoles.TEAM_OWNER.equals(newRole) && teamMemberRepository.countOwners(teamId) <= 1)
            throw createBadRequestException("At least one team owner is required");
        Map<String, String> before = createIdentityMap("teamId", teamId, "team", team.getName(), "userId", userId, "user", getDisplayName(user));
        before.put("role", oldRole);
        putRevision(before, "updatedAt", team.getUpdatedAt());
        putRevision(before, "membershipUpdatedAt", existing == null ? null : existing.getUpdatedAt());
        Map<String, String> after = new LinkedHashMap<>(before);
        after.put("role", newRole);
        return new Prepared(before, after);
    }

    private Prepared prepareProjectMembership(ProposalDraft d) {
        String projectId = requireValue(d.projectId(), "Project ID");
        var project = projectRepository.findById(projectId).orElseThrow(() -> createResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        String subjectType = requireValue(d.subjectType(), "Subject type");
        if (hasText(d.userId()) == hasText(d.teamId()))
            throw createBadRequestException("Supply exactly one userId or teamId");
        String oldRole;
        Map<String, String> before = createIdentityMap("projectId", projectId, "project", project.getName(), "subjectType", subjectType);
        if ("USER".equals(subjectType)) {
            String id = requireValue(d.userId(), "User ID");
            before.put("userId", id);
            before.put("user", getDisplayName(requireMemberUser(id)));
            oldRole = projectMemberRepository.findByProjectIdAndUserId(projectId, id).map(m -> m.getRole()).orElse(null);
        } else if ("TEAM".equals(subjectType)) {
            String id = requireValue(d.teamId(), "Team ID");
            before.put("teamId", id);
            before.put("team", teamService.getTeam(id).getName());
            oldRole = projectTeamRepository.findByProjectIdAndTeamId(projectId, id).map(m -> m.getRole()).orElse(null);
        } else throw createBadRequestException("Subject type must be USER or TEAM");
        validateMembershipAction(d.action(), oldRole);
        String newRole = "REMOVE".equals(d.action()) ? null : normalizeProjectRole(d.role());
        projectAccessService.requireAnotherOwnerBeforeRemovingOwner(projectId, ProjectRoles.OWNER.equals(oldRole) && !ProjectRoles.OWNER.equals(newRole));
        before.put("role", oldRole);
        putRevision(before, "updatedAt", project.getUpdatedAt());
        if ("USER".equals(subjectType))
            projectMemberRepository.findByProjectIdAndUserId(projectId, requireValue(d.userId(), "User ID")).ifPresent(member -> putRevision(before, "membershipUpdatedAt", member.getUpdatedAt()));
        else
            projectTeamRepository.findByProjectIdAndTeamId(projectId, requireValue(d.teamId(), "Team ID")).ifPresent(member -> putRevision(before, "membershipUpdatedAt", member.getUpdatedAt()));
        Map<String, String> after = new LinkedHashMap<>(before);
        after.put("role", newRole);
        return new Prepared(before, after);
    }

    private Prepared prepareUser(ProposalKind kind, ProposalDraft d, AppUser actor) {
        if (!"UPDATE".equals(d.action())) throw createBadRequestException("User proposals support UPDATE only");
        Map<String, String> requested = extractFields(d, kind == ProposalKind.USER_PROFILE ? PROFILE_FIELDS : Set.of("status", "globalRole"));
        UserResponse current = userAdminService.getUser(d.userId(), actor);
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
        UpdateUserRequest request = buildUserUpdateRequest(d, after);
        userAdminService.validateUpdate(d.userId(), request, actor);
        after.put("username", request.getUsername());
        after.put("email", request.getEmail());
        after.put("timezone", request.getTimezone());
        after.put("status", request.getStatus());
        if (requested.containsKey("globalRole"))
            after.put("globalRole", request.getGlobalRole().trim().toUpperCase(Locale.ROOT));
        return new Prepared(before, after);
    }

    private void apply(ProposalKind kind, ProposalDraft d, Map<String, String> expected, Map<String, String> after, AppUser actor) {
        if (proposalWorkflow != null) {
            proposalWorkflow.handler(kind.name()).apply(d, new ProposalPreparedChange(expected, after), actor);
            return;
        }
        applyLegacy(kind, d, expected, after, actor);
    }

    private void applyLegacy(ProposalKind kind, ProposalDraft d, Map<String, String> expected, Map<String, String> after, AppUser actor) {
        switch (kind) {
            case TEAM -> {
                if ("ADD".equals(d.action()))
                    teamService.createTeam(new CreateTeamRequest(after.get("name"), after.get("description"), d.ownerUserIds()), actor);
                else {
                    Team team = new Team();
                    team.setName(after.get("name"));
                    team.setDescription(after.get("description"));
                    teamService.updateTeamIfUnchanged(d.teamId(), team, parseTimestamp(expected.get("updatedAt")), actor);
                }
            }
            case TEAM_MEMBERSHIP ->
                    teamService.applyMembershipOptimistic(d.teamId(), d.userId(), after.get("role"), d.action(), parseTimestamp(expected.get("updatedAt")), parseTimestamp(expected.get("membershipUpdatedAt")), actor);
            case PROJECT_MEMBERSHIP -> {
                if ("USER".equals(d.subjectType()))
                    projectMembershipService.applyUserOptimistic(d.projectId(), d.userId(), after.get("role"), d.action(), parseTimestamp(expected.get("updatedAt")), parseTimestamp(expected.get("membershipUpdatedAt")), actor);
                else
                    projectMembershipService.applyTeamOptimistic(d.projectId(), d.teamId(), after.get("role"), d.action(), parseTimestamp(expected.get("updatedAt")), parseTimestamp(expected.get("membershipUpdatedAt")), actor);
            }
            case USER_PROFILE, USER_ACCESS ->
                    userAdminService.updateUserIfUnchanged(d.userId(), buildUserUpdateRequest(d, after), parseTimestamp(expected.get("updatedAt")), actor);
        }
    }

    private Map<String, String> buildTargetReference(ProposalDraft d, Prepared prepared) {
        Map<String, String> target = new LinkedHashMap<>();
        if (d.teamId() != null) target.put("teamId", d.teamId());
        if (d.userId() != null) target.put("userId", d.userId());
        if (d.projectId() != null) target.put("projectId", d.projectId());
        if (d.subjectType() != null) target.put("subjectType", d.subjectType());
        if (target.isEmpty() && prepared.after().get("name") != null) target.put("name", prepared.after().get("name"));
        return target;
    }

    private Map<String, String> collectRevisions(Map<String, String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : List.of("updatedAt", "membershipUpdatedAt"))
            if (values.containsKey(key)) result.put(key, values.get(key));
        return result;
    }

    private UpdateUserRequest buildUserUpdateRequest(ProposalDraft d, Map<String, String> values) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername(values.get("username"));
        request.setEmail(values.get("email"));
        request.setDisplayName(values.get("displayName"));
        request.setTitle(values.get("title"));
        request.setBio(values.get("bio"));
        request.setTimezone(values.get("timezone"));
        request.setStatus(values.get("status"));
        if (d.fields() != null && d.fields().containsKey("globalRole")) request.setGlobalRole(values.get("globalRole"));
        return request;
    }

    private Map<String, String> extractFields(ProposalDraft d, Set<String> allowed) {
        if (d.fields() == null || d.fields().isEmpty() || !allowed.containsAll(d.fields().keySet()))
            throw createBadRequestException("Unsupported or empty fields");
        if (d.fields().values().stream().anyMatch(v -> v != null && v.length() > 4000))
            throw createBadRequestException("Field values must be 4000 characters or fewer");
        return d.fields();
    }

    private AppUser requireMemberUser(String id) {
        AppUser user = appUserRepository.findById(id).orElseThrow(() -> createResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (AppRoles.isSuperAdmin(user.getGlobalRole())) throw createBadRequestException("Super admin users cannot be members");
        return user;
    }

    private void validateMembershipAction(String action, String oldRole) {
        if ("ADD".equals(action) && oldRole != null)
            throw createResponseStatusException(HttpStatus.CONFLICT, "Membership already exists; use UPDATE with these exact IDs");
        if (!"ADD".equals(action) && oldRole == null) throw createResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
    }

    private String normalizeTeamRole(String role) {
        try {
            return TeamRoles.normalize(requireValue(role, "Role"));
        } catch (IllegalArgumentException e) {
            throw createBadRequestException(e.getMessage());
        }
    }

    private String normalizeProjectRole(String role) {
        try {
            return ProjectRoles.normalize(requireValue(role, "Role"));
        } catch (IllegalArgumentException e) {
            throw createBadRequestException(e.getMessage());
        }
    }

    private String getDisplayName(AppUser user) {
        return user.getDisplayName() == null ? user.getUsername() : user.getDisplayName();
    }

    private Map<String, String> createIdentityMap(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ProposalDraft readDraft(Proposal proposal) {
        return JsonUtils.fromJson(proposal.getDraftJson(), ProposalDraft.class);
    }

    private ProposalDraft readDraft(ProposalChange change) {
        return JsonUtils.fromJson(change.getPayload(), ProposalDraft.class);
    }

    private void putRevision(Map<String, String> values, String key, OffsetDateTime value) {
        if (value != null) values.put(key, value.toString());
    }

    private OffsetDateTime parseTimestamp(String value) {
        return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseMap(String json) {
        return JsonUtils.fromJson(json, LinkedHashMap.class);
    }

    private Map<String, String> parseMapOrEmpty(String json) {
        return json == null ? new LinkedHashMap<>() : parseMap(json);
    }

    private String toAuditOperation(String action) {
        return switch (action) {
            case "ADD" -> "CREATE";
            case "REMOVE" -> "DELETE";
            default -> action;
        };
    }

    private String toProposalAction(String operation) {
        return switch (operation) {
            case "CREATE" -> "ADD";
            case "DELETE" -> "REMOVE";
            default -> operation;
        };
    }

    private String buildRevisionKey(ProposalKind kind, ProposalDraft draft) {
        return switch (kind) {
            case TEAM_MEMBERSHIP -> "TEAM:" + draft.teamId();
            case PROJECT_MEMBERSHIP -> "PROJECT:" + draft.projectId();
            default -> null;
        };
    }

    private boolean hasSameValuesExceptUpdatedAt(Map<String, String> left, Map<String, String> right) {
        Map<String, String> leftComparable = new LinkedHashMap<>(left);
        Map<String, String> rightComparable = new LinkedHashMap<>(right);
        leftComparable.remove("updatedAt");
        rightComparable.remove("updatedAt");
        return leftComparable.equals(rightComparable);
    }

    private ProposalView createView(Proposal proposal, List<ProposalChange> proposalChanges) {
        List<ProposalChangeView> views = proposalChanges.stream().map(change -> new ProposalChangeView(change.getId(), ProposalKind.valueOf(change.getEntityType()), toProposalAction(change.getOperation()), change.getStatus(), parseMapOrEmpty(change.getBeforeSnapshot()), parseMapOrEmpty(change.getAfterSnapshot()))).toList();
        ProposalChangeView first = views.getFirst();
        return new ProposalView(proposal.getId(), proposal.getSourceMessageId(), proposal.getWorkflowType(), first.kind(), proposalChanges.size() == 1 ? first.action() : "BATCH", proposal.getStatus(), first.before(), first.after(), views);
    }

    private ProposalView createLegacyView(Proposal proposal) {
        ProposalDraft draft = readDraft(proposal);
        ProposalChangeView change = new ProposalChangeView(proposal.getId(), ProposalKind.valueOf(proposal.getKind()), draft.action(), proposal.getStatus(), parseMap(proposal.getBeforeJson()), parseMap(proposal.getAfterJson()));
        return new ProposalView(proposal.getId(), proposal.getSourceMessageId(), proposal.getWorkflowType(), change.kind(), change.action(), change.status(), change.before(), change.after(), List.of(change));
    }

    private String requireValue(String value, String label) {
        if (value == null || value.isBlank()) throw createBadRequestException(label + " is required");
        if (!value.equals(value.trim())) throw createBadRequestException(label + " must not contain surrounding whitespace");
        return value;
    }

    private static ResponseStatusException createBadRequestException(String message) {
        return createResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException createResponseStatusException(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
