package com.windrunner.server.work;

import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.proposal.Proposal;
import com.windrunner.server.proposal.ProposalChange;
import com.windrunner.server.proposal.ProposalChangeRepository;
import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.proposal.ProposalRepository;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.work.api.ChangeDraft;
import com.windrunner.server.work.api.DecisionRequest;
import com.windrunner.server.work.api.ProposalDraft;
import com.windrunner.server.work.api.WorkItemPayload;
import com.windrunner.server.work.api.WorkItemView;
import com.windrunner.server.work.api.WorkspaceChangeProposalPage;
import com.windrunner.server.work.api.WorkspaceChangeProposalSummary;
import com.windrunner.server.work.api.WorkspaceChangeProposalView;
import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.proposal.WorkspacePreparedChange;
import com.windrunner.server.work.proposal.WorkspaceProposalChange;
import com.windrunner.server.work.proposal.WorkspaceProposalTarget;
import com.windrunner.server.work.proposal.WorkspaceProposalWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkspaceChangeProposalService {
    private static final int MAX_CHANGES = 100;
    private static final String EMPTY_JSON = "{}";
    private static final Set<String> ENTITY_TYPES = Set.of("WORK_ITEM", "ENTRY", "RELATIONSHIP");
    private static final Set<String> ACTIONS = Set.of("ADD", "UPDATE", "DELETE");
    private static final Set<String> OPEN_STATUSES = Set.of("PENDING", "NEEDS_UPDATE");
    private static final Set<String> MCP_PROPOSAL_STATUSES = Set.of("PENDING", "APPLIED", "REJECTED", "COMPLETED");
    private static final int DEFAULT_MCP_PAGE_LIMIT = 20;
    private static final int MAX_MCP_PAGE_LIMIT = 50;
    private static final int MAX_MCP_SOURCE_TEXT_LENGTH = 500;

    private final ProposalRepository proposalRepository;
    private final ProposalChangeRepository proposalChangeRepository;
    private final WorkspaceProposalWorkflow proposalWorkflow;
    private final EntityIdGenerator entityIdGenerator;

    @Transactional
    public WorkspaceChangeProposalView create(String projectId, String chatSessionId, String sourceMessageId,
                                              String sourceText, AppUser actor, ProposalDraft draft) {
        return createProposal(projectId, chatSessionId, sourceMessageId, sourceText, null, actor, draft);
    }

    @Transactional
    public WorkspaceChangeProposalView createFromMcp(String projectId, String sourceText, String sourceApiKeyId,
                                                     AppUser actor, ProposalDraft draft) {
        if (isBlank(sourceApiKeyId)) throw createBadRequestException("Source API key ID is required");
        String normalizedSourceText = trimToNull(sourceText);
        if (normalizedSourceText != null && normalizedSourceText.length() > MAX_MCP_SOURCE_TEXT_LENGTH) {
            throw createBadRequestException("MCP request summary can contain at most 500 characters");
        }
        return createProposal(projectId, null, null, normalizedSourceText, sourceApiKeyId.trim(), actor, draft);
    }

    private WorkspaceChangeProposalView createProposal(String projectId, String chatSessionId, String sourceMessageId,
                                                        String sourceText, String sourceApiKeyId,
                                                        AppUser actor, ProposalDraft draft) {
        if (draft == null || draft.changes() == null || draft.changes().isEmpty()) {
            throw createBadRequestException("At least one workspace change is required");
        }
        if (draft.changes().size() > MAX_CHANGES) {
            throw createBadRequestException("A proposal can contain at most 100 changes");
        }

        Map<String, String> reservedIds = reserveIds(draft.changes());
        List<PreparedDraft> preparedDrafts = new ArrayList<>();
        for (ChangeDraft requested : draft.changes()) {
            WorkspaceProposalChange change = normalize(projectId, requested, reservedIds);
            ProposalHandler<WorkspaceProposalChange, WorkspacePreparedChange> handler =
                    proposalWorkflow.handler(normalizeEntityType(requested.entityType()));
            handler.authorize(change, actor);
            preparedDrafts.add(new PreparedDraft(change, handler.prepare(change, actor)));
        }

        String proposalId = entityIdGenerator.generate(EntityIdType.PROPOSAL);
        if (sourceApiKeyId == null) {
            proposalRepository.insertWorkspace(proposalId, projectId, chatSessionId, sourceMessageId,
                    sourceText, actor.getId());
        } else {
            proposalRepository.insertMcpWorkspace(proposalId, projectId, trimToNull(sourceText),
                    sourceApiKeyId, actor.getId());
        }
        for (int sortIndex = 0; sortIndex < preparedDrafts.size(); sortIndex++) {
            PreparedDraft preparedDraft = preparedDrafts.get(sortIndex);
            WorkspaceProposalChange change = preparedDraft.change();
            WorkspacePreparedChange prepared = preparedDraft.prepared();
            proposalChangeRepository.insert(
                    entityIdGenerator.generate(EntityIdType.PROPOSAL_CHANGE),
                    proposalId,
                    sortIndex,
                    normalizeEntityType(change.draft().entityType()),
                    change.action(),
                    JsonUtils.toJson(new WorkspaceProposalTarget(projectId, change.targetId(), change.summary())),
                    prepared.payloadJson(),
                    valueOrEmptyJson(prepared.previousJson()),
                    prepared.payloadJson(),
                    valueOrEmptyJson(prepared.baseVersionJson()));
        }
        return sourceApiKeyId == null
                ? get(projectId, proposalId)
                : getFromMcp(projectId, proposalId, sourceApiKeyId);
    }

    public List<WorkspaceChangeProposalView> list(String projectId) {
        return proposalRepository.findWorkspaceByProjectId(projectId).stream()
                .map(this::createView)
                .toList();
    }

    public List<WorkspaceChangeProposalView> list(String projectId, String chatSessionId) {
        if (isBlank(chatSessionId)) return list(projectId);
        return proposalRepository.findWorkspaceByProjectIdAndChatSessionId(projectId, chatSessionId.trim()).stream()
                .map(this::createView)
                .toList();
    }

    public WorkspaceChangeProposalView get(String projectId, String proposalId) {
        Proposal proposal = proposalRepository.findWorkspaceInProject(proposalId, projectId)
                .orElseThrow(() -> createNotFoundException("Workspace proposal not found"));
        return createView(proposal);
    }

    public WorkspaceChangeProposalPage listFromMcp(String projectId, String sourceApiKeyId, String status,
                                                   Integer requestedLimit, Long requestedOffset) {
        if (isBlank(sourceApiKeyId)) throw createBadRequestException("Source API key ID is required");
        String normalizedStatus = normalizeToken(status);
        if (!normalizedStatus.isEmpty() && !MCP_PROPOSAL_STATUSES.contains(normalizedStatus)) {
            throw createBadRequestException("Status must be PENDING, APPLIED, REJECTED, or COMPLETED");
        }
        int limit = requestedLimit == null
                ? DEFAULT_MCP_PAGE_LIMIT
                : Math.max(1, Math.min(requestedLimit, MAX_MCP_PAGE_LIMIT));
        long offset = requestedOffset == null ? 0 : Math.max(0, requestedOffset);
        List<WorkspaceChangeProposalSummary> proposals = proposalRepository.pageMcpWorkspace(
                        sourceApiKeyId.trim(), projectId, normalizedStatus, limit, offset).stream()
                .map(WorkspaceChangeProposalSummary::of)
                .toList();
        long total = proposalRepository.countMcpWorkspace(sourceApiKeyId.trim(), projectId, normalizedStatus);
        return new WorkspaceChangeProposalPage(proposals, proposals.size(), total, limit, offset,
                offset + proposals.size() < total);
    }

    public WorkspaceChangeProposalView getFromMcp(String projectId, String proposalId, String sourceApiKeyId) {
        if (isBlank(sourceApiKeyId)) throw createBadRequestException("Source API key ID is required");
        Proposal proposal = proposalRepository.findMcpWorkspaceInProject(
                        proposalId, projectId, sourceApiKeyId.trim())
                .orElseThrow(() -> createNotFoundException("Workspace proposal not found"));
        return createView(proposal);
    }

    public List<String> findMcpEntityTypes(String projectId, String proposalId, String sourceApiKeyId) {
        if (isBlank(sourceApiKeyId)) throw createBadRequestException("Source API key ID is required");
        List<String> entityTypes = proposalRepository.findMcpWorkspaceEntityTypes(
                proposalId, projectId, sourceApiKeyId.trim());
        if (entityTypes.isEmpty()) throw createNotFoundException("Workspace proposal not found");
        return entityTypes;
    }

    @Transactional
    public WorkspaceChangeProposalView createRevert(String projectId, String proposalId, AppUser actor) {
        return createRevertProposal(projectId, proposalId, null, actor);
    }

    @Transactional
    public WorkspaceChangeProposalView createRevertFromMcp(String projectId, String proposalId,
                                                           String sourceApiKeyId, AppUser actor) {
        if (isBlank(sourceApiKeyId)) throw createBadRequestException("Source API key ID is required");
        return createRevertProposal(projectId, proposalId, sourceApiKeyId.trim(), actor);
    }

    private WorkspaceChangeProposalView createRevertProposal(String projectId, String proposalId,
                                                              String sourceApiKeyId, AppUser actor) {
        Proposal original = (sourceApiKeyId == null
                ? proposalRepository.findWorkspaceInProjectForUpdate(proposalId, projectId)
                : proposalRepository.findMcpWorkspaceInProjectForUpdate(proposalId, projectId, sourceApiKeyId))
                .orElseThrow(() -> createNotFoundException("Workspace proposal not found"));
        validateRevertProposal(original);
        List<ProposalChange> appliedChanges = proposalChangeRepository.findByProposalId(proposalId).stream()
                .filter(change -> "APPLIED".equals(change.getStatus()))
                .toList();
        if (appliedChanges.isEmpty()) {
            throw createConflictException("Workspace proposal has no applied changes to revert");
        }

        List<PreparedDraft> preparedDrafts = new ArrayList<>();
        for (ProposalChange appliedChange : appliedChanges) {
            ProposalHandler<WorkspaceProposalChange, WorkspacePreparedChange> handler =
                    proposalWorkflow.handler(appliedChange.getEntityType());
            WorkspaceProposalTarget originalTarget = parseTarget(appliedChange);
            if (!projectId.equals(originalTarget.projectId())) {
                throw createBadRequestException("Workspace proposal change belongs to another project");
            }
            handler.authorize(new WorkspaceProposalChange(projectId, appliedChange.getOperation(),
                    originalTarget.targetId(), originalTarget.summary(), null, Map.of()), actor);
            WorkspaceProposalChange revertChange = handler.buildRevert(appliedChange, actor);
            if (!projectId.equals(revertChange.projectId())) {
                throw createBadRequestException("Workspace proposal change belongs to another project");
            }
            handler.authorize(revertChange, actor);
            preparedDrafts.add(new PreparedDraft(revertChange, handler.prepare(revertChange, actor)));
        }

        String revertId = entityIdGenerator.generate(EntityIdType.PROPOSAL);
        if (sourceApiKeyId == null) {
            proposalRepository.insertWorkspaceRevert(revertId, projectId, original.getChatSessionId(),
                    original.getSourceMessageId(), original.getSourceText(), original.getId(), actor.getId());
        } else {
            proposalRepository.insertMcpWorkspaceRevert(revertId, projectId, original.getSourceText(),
                    sourceApiKeyId, original.getId(), actor.getId());
        }
        for (int sortIndex = 0; sortIndex < preparedDrafts.size(); sortIndex++) {
            PreparedDraft preparedDraft = preparedDrafts.get(sortIndex);
            WorkspaceProposalChange change = preparedDraft.change();
            WorkspacePreparedChange prepared = preparedDraft.prepared();
            proposalChangeRepository.insert(
                    entityIdGenerator.generate(EntityIdType.PROPOSAL_CHANGE),
                    revertId,
                    sortIndex,
                    normalizeEntityType(change.draft().entityType()),
                    change.action(),
                    JsonUtils.toJson(new WorkspaceProposalTarget(projectId, change.targetId(), change.summary())),
                    prepared.payloadJson(),
                    valueOrEmptyJson(prepared.previousJson()),
                    prepared.payloadJson(),
                    valueOrEmptyJson(prepared.baseVersionJson()));
        }
        return sourceApiKeyId == null
                ? get(projectId, revertId)
                : getFromMcp(projectId, revertId, sourceApiKeyId);
    }

    @Transactional
    public WorkspaceChangeProposalView decide(String projectId, String proposalId, String changeId,
                                              DecisionRequest request, AppUser actor) {
        validateDecisionRequest(request);
        proposalRepository.findWorkspaceInProjectForUpdate(proposalId, projectId)
                .orElseThrow(() -> createNotFoundException("Workspace proposal not found"));
        ProposalChange storedChange = proposalChangeRepository.findInProposal(changeId, proposalId)
                .orElseThrow(() -> createNotFoundException("Workspace proposal change not found"));
        if (!OPEN_STATUSES.contains(storedChange.getStatus())) {
            throw createBadRequestException("This proposal change has already been decided");
        }

        String decision = normalizeToken(request.decision());
        String status;
        if ("ACCEPT".equals(decision)) {
            apply(projectId, storedChange, actor);
            status = "APPLIED";
        } else if ("REJECT".equals(decision)) {
            status = "REJECTED";
        } else if ("REQUEST_UPDATE".equals(decision)) {
            if (isBlank(request.feedback())) {
                throw createBadRequestException("Feedback is required when requesting an update");
            }
            status = "NEEDS_UPDATE";
        } else {
            throw createBadRequestException("Proposal decision must be ACCEPT, REJECT, or REQUEST_UPDATE");
        }

        int updated = proposalChangeRepository.decide(changeId, proposalId, status, trimToNull(request.feedback()));
        if (updated != 1) throw createConflictException("This proposal change has already been decided");
        refreshProposalStatus(projectId, proposalId, actor.getId());
        return get(projectId, proposalId);
    }

    @Transactional
    public WorkspaceChangeProposalView decideAll(String projectId, String proposalId,
                                                 DecisionRequest request, AppUser actor) {
        return decideAllChanges(projectId, proposalId, request, null, actor);
    }

    @Transactional
    public WorkspaceChangeProposalView decideAllFromMcp(String projectId, String proposalId,
                                                        DecisionRequest request, String sourceApiKeyId,
                                                        AppUser actor) {
        if (isBlank(sourceApiKeyId)) throw createBadRequestException("Source API key ID is required");
        return decideAllChanges(projectId, proposalId, request, sourceApiKeyId.trim(), actor);
    }

    private WorkspaceChangeProposalView decideAllChanges(String projectId, String proposalId,
                                                          DecisionRequest request, String sourceApiKeyId,
                                                          AppUser actor) {
        validateDecisionRequest(request);
        (sourceApiKeyId == null
                ? proposalRepository.findWorkspaceInProjectForUpdate(proposalId, projectId)
                : proposalRepository.findMcpWorkspaceInProjectForUpdate(proposalId, projectId, sourceApiKeyId))
                .orElseThrow(() -> createNotFoundException("Workspace proposal not found"));
        List<ProposalChange> openChanges = proposalChangeRepository.findByProposalId(proposalId).stream()
                .filter(change -> OPEN_STATUSES.contains(change.getStatus()))
                .toList();
        if (openChanges.isEmpty()) {
            throw createBadRequestException("This workspace proposal has already been decided");
        }

        String decision = normalizeToken(request.decision());
        if (!Set.of("ACCEPT", "REJECT").contains(decision)) {
            throw createBadRequestException("Workspace proposal decision must be ACCEPT or REJECT");
        }

        List<ProposalChange> changes = "ACCEPT".equals(decision)
                ? openChanges.stream()
                .sorted(Comparator.comparingInt(this::applicationOrder)
                        .thenComparingInt(ProposalChange::getSortIndex))
                .toList()
                : openChanges;
        String status = "ACCEPT".equals(decision) ? "APPLIED" : "REJECTED";
        for (ProposalChange change : changes) {
            if ("ACCEPT".equals(decision)) apply(projectId, change, actor);
            int updated = proposalChangeRepository.decide(change.getId(), proposalId, status, null);
            if (updated != 1) throw createConflictException("This proposal change has already been decided");
        }
        refreshProposalStatus(projectId, proposalId, actor.getId());
        return sourceApiKeyId == null
                ? get(projectId, proposalId)
                : getFromMcp(projectId, proposalId, sourceApiKeyId);
    }

    private void apply(String projectId, ProposalChange storedChange, AppUser actor) {
        WorkspaceProposalTarget target = parseTarget(storedChange);
        if (!projectId.equals(target.projectId())) {
            throw createBadRequestException("Workspace proposal change belongs to another project");
        }
        WorkspaceProposalChange change = new WorkspaceProposalChange(
                projectId,
                storedChange.getOperation(),
                target.targetId(),
                target.summary(),
                null,
                Map.of());
        ProposalHandler<WorkspaceProposalChange, WorkspacePreparedChange> handler =
                proposalWorkflow.handler(storedChange.getEntityType());
        handler.authorize(change, actor);
        handler.apply(change, new WorkspacePreparedChange(
                storedChange.getPayload(),
                nullableSnapshot(storedChange.getBeforeSnapshot()),
                storedChange.getBaseVersion()), actor);
    }

    private Map<String, String> reserveIds(List<ChangeDraft> requested) {
        Map<String, String> reservedIds = new LinkedHashMap<>();
        for (ChangeDraft change : requested) {
            if (change == null || !"ADD".equals(normalizeToken(change.action()))) continue;
            String entityType = normalizeEntityType(change.entityType());
            if (isBlank(change.clientRef())) throw createBadRequestException("New records require a clientRef");
            String reservedId = switch (entityType) {
                case "WORK_ITEM" -> entityIdGenerator.generate(EntityIdType.WORK_ITEM);
                case "ENTRY" -> entityIdGenerator.generate(EntityIdType.ENTRY);
                case "RELATIONSHIP" -> entityIdGenerator.generate(EntityIdType.RELATIONSHIP);
                default -> throw createBadRequestException("Unsupported proposal entity type");
            };
            if (reservedIds.putIfAbsent(change.clientRef().trim(), reservedId) != null) {
                throw createBadRequestException("Proposal clientRef values must be unique");
            }
        }
        return reservedIds;
    }

    private WorkspaceProposalChange normalize(String projectId, ChangeDraft requested,
                                              Map<String, String> reservedIds) {
        if (requested == null) throw createBadRequestException("Workspace change is required");
        String entityType = normalizeEntityType(requested.entityType());
        String action = normalizeToken(requested.action());
        if (!ACTIONS.contains(action)) {
            throw createBadRequestException("Change action must be ADD, UPDATE, or DELETE");
        }
        String targetId = "ADD".equals(action)
                ? reservedIds.get(trimToNull(requested.clientRef()))
                : trimToNull(requested.targetId());
        if (isBlank(targetId)) throw createBadRequestException("Existing records require a targetId");
        String summary = isBlank(requested.summary())
                ? action + " " + entityType.toLowerCase().replace('_', ' ')
                : requested.summary().trim();
        return new WorkspaceProposalChange(projectId, action, targetId, summary, requested, reservedIds);
    }

    private int applicationOrder(ProposalChange change) {
        if ("WORK_ITEM".equals(change.getEntityType()) && "ADD".equals(change.getOperation())) return 0;
        if ("WORK_ITEM".equals(change.getEntityType()) && "UPDATE".equals(change.getOperation())) return 50;
        if ("ENTRY".equals(change.getEntityType()) && !"DELETE".equals(change.getOperation())) return 100;
        if ("RELATIONSHIP".equals(change.getEntityType()) && !"DELETE".equals(change.getOperation())) return 200;
        if ("RELATIONSHIP".equals(change.getEntityType())) return 300;
        if ("ENTRY".equals(change.getEntityType())) return 400;
        return 500;
    }

    private void validateRevertProposal(Proposal proposal) {
        if (proposal.getRevertsProposalId() != null) {
            throw createBadRequestException("A revert proposal cannot be reverted");
        }
        if (!Set.of("APPLIED", "COMPLETED").contains(proposal.getStatus())) {
            throw createConflictException("Only an applied workspace proposal can be reverted");
        }
        if (proposalRepository.findActiveRevert(proposal.getId()).isPresent()) {
            throw createConflictException("This workspace proposal already has a pending or applied revert");
        }
    }

    private void refreshProposalStatus(String projectId, String proposalId, String actorId) {
        List<ProposalChange> changes = proposalChangeRepository.findByProposalId(proposalId);
        String status;
        if (changes.stream().anyMatch(change -> OPEN_STATUSES.contains(change.getStatus()))) {
            status = "PENDING";
        } else if (changes.stream().allMatch(change -> "APPLIED".equals(change.getStatus()))) {
            status = "APPLIED";
        } else if (changes.stream().allMatch(change -> "REJECTED".equals(change.getStatus()))) {
            status = "REJECTED";
        } else {
            status = "COMPLETED";
        }
        if (proposalRepository.updateWorkspaceStatus(proposalId, projectId, status, actorId) != 1) {
            throw createNotFoundException("Workspace proposal not found");
        }
    }

    private WorkspaceChangeProposalView createView(Proposal proposal) {
        List<WorkspaceChangeProposalView.ChangeView> changes = proposalChangeRepository.findByProposalId(proposal.getId())
                .stream()
                .map(this::createChangeView)
                .toList();
        return WorkspaceChangeProposalView.of(proposal, changes);
    }

    private WorkspaceChangeProposalView.ChangeView createChangeView(ProposalChange change) {
        WorkspaceProposalTarget target = parseTarget(change);
        WorkItemView workItem = null;
        WorkItemView previousWorkItem = null;
        Entry entry = null;
        Entry previousEntry = null;
        Relationship relationship = null;
        Relationship previousRelationship = null;

        if ("WORK_ITEM".equals(change.getEntityType())) {
            WorkItemPayload payload = JsonUtils.fromJson(change.getPayload(), WorkItemPayload.class);
            WorkItemPayload previous = parseNullable(change.getBeforeSnapshot(), WorkItemPayload.class);
            workItem = new WorkItemView(payload.workItem(), payload.assignees());
            if (previous != null) previousWorkItem = new WorkItemView(previous.workItem(), previous.assignees());
        } else if ("ENTRY".equals(change.getEntityType())) {
            entry = JsonUtils.fromJson(change.getPayload(), Entry.class);
            previousEntry = parseNullable(change.getBeforeSnapshot(), Entry.class);
        } else if ("RELATIONSHIP".equals(change.getEntityType())) {
            relationship = JsonUtils.fromJson(change.getPayload(), Relationship.class);
            previousRelationship = parseNullable(change.getBeforeSnapshot(), Relationship.class);
        } else {
            throw createBadRequestException("Unsupported workspace proposal entity type");
        }

        return new WorkspaceChangeProposalView.ChangeView(
                change.getId(),
                change.getSortIndex(),
                change.getEntityType(),
                change.getOperation(),
                target.targetId(),
                target.summary(),
                change.getStatus(),
                change.getFeedback(),
                change.getAppliedAt(),
                change.getCreatedAt(),
                change.getUpdatedAt(),
                workItem,
                previousWorkItem,
                entry,
                previousEntry,
                relationship,
                previousRelationship);
    }

    private WorkspaceProposalTarget parseTarget(ProposalChange change) {
        return JsonUtils.fromJson(change.getTargetRef(), WorkspaceProposalTarget.class);
    }

    private <T> T parseNullable(String json, Class<T> type) {
        return isEmptyJson(json) ? null : JsonUtils.fromJson(json, type);
    }

    private String nullableSnapshot(String json) {
        return isEmptyJson(json) ? null : json;
    }

    private boolean isEmptyJson(String json) {
        return json == null || json.isBlank() || EMPTY_JSON.equals(json.trim());
    }

    private String valueOrEmptyJson(String json) {
        return json == null ? EMPTY_JSON : json;
    }

    private void validateDecisionRequest(DecisionRequest request) {
        if (request == null || isBlank(request.decision())) {
            throw createBadRequestException("Proposal decision is required");
        }
    }

    private String normalizeEntityType(String value) {
        String normalized = normalizeToken(value);
        if (!ENTITY_TYPES.contains(normalized)) {
            throw createBadRequestException("Entity type must be WORK_ITEM, ENTRY, or RELATIONSHIP");
        }
        return normalized;
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException createBadRequestException(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException createNotFoundException(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException createConflictException(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record PreparedDraft(WorkspaceProposalChange change, WorkspacePreparedChange prepared) {
    }
}
