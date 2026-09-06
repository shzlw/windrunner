package com.windrunner.server.work;

import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.work.api.AssigneeDraft;
import com.windrunner.server.work.api.ChangeDraft;
import com.windrunner.server.work.api.DecisionRequest;
import com.windrunner.server.work.api.EntryDraft;
import com.windrunner.server.work.api.ProposalDraft;
import com.windrunner.server.work.api.RelationshipDraft;
import com.windrunner.server.work.api.WorkItemDraft;
import com.windrunner.server.work.api.WorkItemPayload;
import com.windrunner.server.work.api.WorkItemView;
import com.windrunner.server.work.api.WorkspaceChangeProposalView;
import com.windrunner.server.work.domain.*;
import com.windrunner.server.work.persistence.WorkspaceChangeProposalRepository;
import com.windrunner.server.work.persistence.WorkspaceChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkspaceChangeProposalService {
    private static final Set<String> ENTITY_TYPES = Set.of("WORK_ITEM", "ENTRY", "RELATIONSHIP");
    private static final Set<String> ACTIONS = Set.of("ADD", "UPDATE", "DELETE");

    private final WorkspaceChangeProposalRepository workspaceChangeProposalRepository;
    private final WorkspaceChangeRepository workspaceChangeRepository;
    private final WorkItemService workItemService;
    private final EntryService entryService;
    private final RelationshipService relationshipService;
    private final EntityIdGenerator entityIdGenerator;

    @Transactional
    public WorkspaceChangeProposalView create(String projectId, String chatSessionId, String sourceMessageId,
                                              String sourceText, ProposalDraft draft) {
        if (draft == null || draft.changes() == null || draft.changes().isEmpty()) {
            throw WorkItemService.createBadRequestException("At least one workspace change is required");
        }
        if (draft.changes().size() > 100) throw WorkItemService.createBadRequestException("A proposal can contain at most 100 changes");

        String proposalId = entityIdGenerator.generate(EntityIdType.WORKSPACE_CHANGE_PROPOSAL);
        workspaceChangeProposalRepository.insert(proposalId, projectId, chatSessionId, sourceMessageId, sourceText);

        Map<String, String> reservedIds = reserveIds(draft.changes());
        int sortIndex = 0;
        for (ChangeDraft requested : draft.changes()) {
            NormalizedChange normalized = normalize(projectId, requested, reservedIds);
            workspaceChangeRepository.insert(entityIdGenerator.generate(EntityIdType.WORKSPACE_CHANGE), proposalId, projectId, sortIndex++,
                    normalized.entityType(), normalized.action(), normalized.targetId(), normalized.summary(),
                    normalized.payloadJson(), normalized.previousJson());
        }
        return get(projectId, proposalId);
    }

    public List<WorkspaceChangeProposalView> list(String projectId) {
        return workspaceChangeProposalRepository.findByProjectId(projectId).stream().map(proposal -> createView(proposal, workspaceChangeRepository.findByProposalId(proposal.getId()))).toList();
    }

    public WorkspaceChangeProposalView get(String projectId, String proposalId) {
        WorkspaceChangeProposal proposal = workspaceChangeProposalRepository.findInProject(proposalId, projectId)
                .orElseThrow(() -> WorkItemService.createNotFoundException("Workspace proposal not found"));
        return createView(proposal, workspaceChangeRepository.findByProposalId(proposalId));
    }

    @Transactional
    public WorkspaceChangeProposalView decide(String projectId, String proposalId, String changeId,
                                              DecisionRequest request, String actorId) {
        if (request == null || WorkItemService.isBlank(request.decision()))
            throw WorkItemService.createBadRequestException("Proposal decision is required");
        WorkspaceChange change = workspaceChangeRepository.findInProposal(changeId, proposalId, projectId)
                .orElseThrow(() -> WorkItemService.createNotFoundException("Workspace proposal change not found"));
        if (!Set.of("PENDING", "NEEDS_UPDATE").contains(change.getStatus())) {
            throw WorkItemService.createBadRequestException("This proposal change has already been decided");
        }

        String decision = request.decision().trim().toUpperCase();
        String status;
        if ("ACCEPT".equals(decision)) {
            apply(projectId, change, actorId);
            status = "APPLIED";
        } else if ("REJECT".equals(decision)) {
            status = "REJECTED";
        } else if ("REQUEST_UPDATE".equals(decision)) {
            if (WorkItemService.isBlank(request.feedback()))
                throw WorkItemService.createBadRequestException("Feedback is required when requesting an update");
            status = "NEEDS_UPDATE";
        } else {
            throw WorkItemService.createBadRequestException("Proposal decision must be ACCEPT, REJECT, or REQUEST_UPDATE");
        }
        workspaceChangeRepository.decide(changeId, proposalId, projectId, status,
                WorkItemService.isBlank(request.feedback()) ? null : request.feedback().trim());
        refreshProposalStatus(projectId, proposalId);
        return get(projectId, proposalId);
    }

    private void apply(String projectId, WorkspaceChange change, String actorId) {
        switch (change.getEntityType()) {
            case "WORK_ITEM" -> applyWorkItem(projectId, change, actorId);
            case "ENTRY" -> applyEntry(projectId, change, actorId);
            case "RELATIONSHIP" -> applyRelationship(projectId, change, actorId);
            default -> throw WorkItemService.createBadRequestException("Unsupported proposal entity type");
        }
    }

    private void applyWorkItem(String projectId, WorkspaceChange change, String actorId) {
        WorkItemPayload payload = parseJson(change.getPayloadJson(), WorkItemPayload.class);
        if ("ADD".equals(change.getAction())) {
            workItemService.createWithId(projectId, change.getTargetId(), payload.workItem(), payload.assignees(), actorId);
        } else if ("UPDATE".equals(change.getAction())) {
            requireUnchangedWorkItem(projectId, change);
            workItemService.update(projectId, change.getTargetId(), payload.workItem(), payload.assignees(), actorId);
        } else {
            requireUnchangedWorkItem(projectId, change);
            workItemService.delete(projectId, change.getTargetId(), actorId);
        }
    }

    private void applyEntry(String projectId, WorkspaceChange change, String actorId) {
        Entry payload = parseJson(change.getPayloadJson(), Entry.class);
        if ("ADD".equals(change.getAction())) entryService.createWithId(projectId, change.getTargetId(), payload, actorId);
        else if ("UPDATE".equals(change.getAction())) {
            requireUnchangedEntry(projectId, change);
            entryService.update(projectId, change.getTargetId(), payload, actorId);
        } else {
            requireUnchangedEntry(projectId, change);
            entryService.delete(projectId, change.getTargetId(), actorId);
        }
    }

    private void applyRelationship(String projectId, WorkspaceChange change, String actorId) {
        Relationship payload = parseJson(change.getPayloadJson(), Relationship.class);
        if ("ADD".equals(change.getAction()))
            relationshipService.createWithId(projectId, change.getTargetId(), payload, actorId);
        else if ("UPDATE".equals(change.getAction())) {
            requireUnchangedRelationship(projectId, change);
            relationshipService.updateReason(projectId, change.getTargetId(), payload.getReason(), actorId);
        } else {
            requireUnchangedRelationship(projectId, change);
            relationshipService.delete(projectId, change.getTargetId(), actorId);
        }
    }

    private void requireUnchangedWorkItem(String projectId, WorkspaceChange change) {
        WorkItemPayload before = parseJson(change.getPreviousJson(), WorkItemPayload.class);
        WorkItem current = workItemService.get(projectId, change.getTargetId());
        if (!Objects.equals(current.getUpdatedAt(), before.workItem().getUpdatedAt())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Work item changed after this AI suggestion was created. Ask AI to review it again.");
        }
    }

    private void requireUnchangedEntry(String projectId, WorkspaceChange change) {
        Entry before = parseJson(change.getPreviousJson(), Entry.class);
        Entry current = entryService.get(projectId, change.getTargetId());
        if (!Objects.equals(current.getUpdatedAt(), before.getUpdatedAt())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Entry changed after this AI suggestion was created. Ask AI to review it again.");
        }
    }

    private void requireUnchangedRelationship(String projectId, WorkspaceChange change) {
        Relationship before = parseJson(change.getPreviousJson(), Relationship.class);
        Relationship current = relationshipService.list(projectId).stream().filter(candidate -> change.getTargetId().equals(candidate.getId())).findFirst()
                .orElseThrow(() -> WorkItemService.createNotFoundException("Relationship not found"));
        if (!Objects.equals(current, before)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Relationship changed after this AI suggestion was created. Ask AI to review it again.");
        }
    }

    private Map<String, String> reserveIds(List<ChangeDraft> requested) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ChangeDraft change : requested) {
            if (change == null || !"ADD".equals(normalizeToken(change.action()))) continue;
            String entityType = normalizeEntityType(change.entityType());
            if (WorkItemService.isBlank(change.clientRef())) throw WorkItemService.createBadRequestException("New records require a clientRef");
            String reserved = switch (entityType) {
                case "WORK_ITEM" -> entityIdGenerator.generate(EntityIdType.WORK_ITEM);
                case "ENTRY" -> entityIdGenerator.generate(EntityIdType.ENTRY);
                case "RELATIONSHIP" -> entityIdGenerator.generate(EntityIdType.RELATIONSHIP);
                default -> throw WorkItemService.createBadRequestException("Unsupported proposal entity type");
            };
            if (result.putIfAbsent(change.clientRef().trim(), reserved) != null) {
                throw WorkItemService.createBadRequestException("Proposal clientRef values must be unique");
            }
        }
        return result;
    }

    private NormalizedChange normalize(String projectId, ChangeDraft change, Map<String, String> reservedIds) {
        if (change == null) throw WorkItemService.createBadRequestException("Workspace change is required");
        String entityType = normalizeEntityType(change.entityType());
        String action = normalizeToken(change.action());
        if (!ACTIONS.contains(action)) throw WorkItemService.createBadRequestException("Change action must be ADD, UPDATE, or DELETE");
        String targetId = "ADD".equals(action) ? reservedIds.get(trimToNull(change.clientRef())) : trimToNull(change.targetId());
        if (WorkItemService.isBlank(targetId)) throw WorkItemService.createBadRequestException("Existing records require a targetId");
        String summary = WorkItemService.isBlank(change.summary()) ? action + " " + entityType.toLowerCase().replace('_', ' ') : change.summary().trim();

        return switch (entityType) {
            case "WORK_ITEM" -> normalizeWorkItem(projectId, action, targetId, summary, change.workItem(), reservedIds);
            case "ENTRY" -> normalizeEntry(projectId, action, targetId, summary, change.entry(), reservedIds);
            case "RELATIONSHIP" ->
                    normalizeRelationship(projectId, action, targetId, summary, change.relationship(), reservedIds);
            default -> throw WorkItemService.createBadRequestException("Unsupported proposal entity type");
        };
    }

    private NormalizedChange normalizeWorkItem(String projectId, String action, String targetId, String summary,
                                               WorkItemDraft draft, Map<String, String> reservedIds) {
        WorkItemPayload previous = null;
        WorkItem item;
        List<WorkItemAssignee> assignees;
        if ("ADD".equals(action)) {
            if (draft == null || WorkItemService.isBlank(draft.title()))
                throw WorkItemService.createBadRequestException("New work items require a title");
            item = new WorkItem();
            item.setId(targetId);
            item.setProjectId(projectId);
            item.setTitle(draft.title().trim());
            item.setType(normalizeWorkItemType(draft.type()));
            item.setStatus(valueOrDefault(draft.status(), "OPEN"));
            item.setDueDate(parseDate(draft.dueDate()));
            item.setPriority(convertBlankToNull(draft.priority()));
            item.setParentWorkItemId(resolveNullableRef(draft.parentWorkItemId(), reservedIds));
            assignees = buildAssignees(draft.assignees());
        } else {
            WorkItem current = workItemService.get(projectId, targetId);
            List<WorkItemAssignee> currentAssignees = workItemService.findAssignees(targetId);
            previous = new WorkItemPayload(current, currentAssignees);
            item = copyWorkItem(current);
            assignees = currentAssignees;
            if (!"DELETE".equals(action)) {
                if (draft == null) throw WorkItemService.createBadRequestException("Work item updates require proposed values");
                if (!WorkItemService.isBlank(draft.title())) item.setTitle(draft.title().trim());
                if (!WorkItemService.isBlank(draft.type())) item.setType(normalizeWorkItemType(draft.type()));
                if (!WorkItemService.isBlank(draft.status())) item.setStatus(draft.status());
                if (draft.dueDate() != null) item.setDueDate(parseDate(draft.dueDate()));
                if (draft.priority() != null) item.setPriority(convertBlankToNull(draft.priority()));
                if (draft.parentWorkItemId() != null)
                    item.setParentWorkItemId(resolveNullableRef(draft.parentWorkItemId(), reservedIds));
                if (draft.assignees() != null) assignees = buildAssignees(draft.assignees());
            }
        }
        WorkItemService.normalizeEnumValue(item.getType(), WorkTypes.WORK_ITEM_TYPES, "Work item type");
        WorkItemService.normalizeEnumValue(item.getStatus(), WorkTypes.WORK_ITEM_STATUSES, "Work item status");
        WorkItemPayload payload = new WorkItemPayload(item, assignees);
        return createNormalizedChange("WORK_ITEM", action, targetId, summary, payload, previous);
    }

    private NormalizedChange normalizeEntry(String projectId, String action, String targetId, String summary,
                                            EntryDraft draft, Map<String, String> reservedIds) {
        Entry previous = null;
        Entry entry = new Entry();
        if ("ADD".equals(action)) {
            if (draft == null || WorkItemService.isBlank(draft.body()) || WorkItemService.isBlank(draft.workItemId())) {
                throw WorkItemService.createBadRequestException("New entries require workItemId and body");
            }
            entry.setId(targetId);
            entry.setProjectId(projectId);
            entry.setWorkItemId(resolveRef(draft.workItemId(), reservedIds));
            entry.setType(valueOrDefault(draft.type(), "COMMENT"));
            entry.setBody(draft.body().trim());
        } else {
            previous = entryService.get(projectId, targetId);
            entry = copyEntry(previous);
            if (!"DELETE".equals(action)) {
                if (draft == null) throw WorkItemService.createBadRequestException("Entry updates require proposed values");
                if (!WorkItemService.isBlank(draft.type())) entry.setType(draft.type());
                if (!WorkItemService.isBlank(draft.body())) entry.setBody(draft.body().trim());
            }
        }
        WorkItemService.normalizeEnumValue(entry.getType(), WorkTypes.ENTRY_TYPES, "Entry type");
        return createNormalizedChange("ENTRY", action, targetId, summary, entry, previous);
    }

    private NormalizedChange normalizeRelationship(String projectId, String action, String targetId, String summary,
                                                   RelationshipDraft draft, Map<String, String> reservedIds) {
        Relationship previous = null;
        Relationship relationship = new Relationship();
        if ("ADD".equals(action)) {
            if (draft == null) throw WorkItemService.createBadRequestException("New relationships require proposed values");
            relationship.setId(targetId);
            relationship.setProjectId(projectId);
            relationship.setFromEntityType(normalizeRelationshipEntityType(draft.fromEntityType()));
            relationship.setFromEntityId(resolveRef(draft.fromEntityId(), reservedIds));
            relationship.setToEntityType(normalizeRelationshipEntityType(draft.toEntityType()));
            relationship.setToEntityId(resolveRef(draft.toEntityId(), reservedIds));
            relationship.setType(WorkItemService.normalizeEnumValue(draft.type(), WorkTypes.RELATIONSHIP_TYPES, "Relationship type"));
            relationship.setReason(convertBlankToNull(draft.reason()));
            relationship.setSourceEntryId(resolveNullableRef(draft.sourceEntryId(), reservedIds));
        } else {
            previous = relationshipService.list(projectId).stream().filter(candidate -> targetId.equals(candidate.getId())).findFirst()
                    .orElseThrow(() -> WorkItemService.createNotFoundException("Relationship not found"));
            relationship = copyRelationship(previous);
            if ("UPDATE".equals(action) && draft != null && draft.reason() != null)
                relationship.setReason(convertBlankToNull(draft.reason()));
        }
        return createNormalizedChange("RELATIONSHIP", action, targetId, summary, relationship, previous);
    }

    private void refreshProposalStatus(String projectId, String proposalId) {
        List<WorkspaceChange> all = workspaceChangeRepository.findByProposalId(proposalId);
        String status = all.stream().anyMatch(change -> Set.of("PENDING", "NEEDS_UPDATE").contains(change.getStatus())) ? "PENDING" : "COMPLETED";
        workspaceChangeProposalRepository.updateStatus(proposalId, projectId, status);
    }

    private WorkspaceChangeProposalView createView(WorkspaceChangeProposal proposal, List<WorkspaceChange> storedChanges) {
        List<WorkspaceChangeProposalView.ChangeView> views = new ArrayList<>();
        for (WorkspaceChange change : storedChanges) {
            WorkItemView workItem = null;
            WorkItemView previousWorkItem = null;
            Entry entry = null;
            Entry previousEntry = null;
            Relationship relationship = null;
            Relationship previousRelationship = null;
            if ("WORK_ITEM".equals(change.getEntityType())) {
                WorkItemPayload payload = parseJson(change.getPayloadJson(), WorkItemPayload.class);
                WorkItemPayload before = parseNullableJson(change.getPreviousJson(), WorkItemPayload.class);
                workItem = new WorkItemView(payload.workItem(), payload.assignees());
                if (before != null) previousWorkItem = new WorkItemView(before.workItem(), before.assignees());
            } else if ("ENTRY".equals(change.getEntityType())) {
                entry = parseJson(change.getPayloadJson(), Entry.class);
                previousEntry = parseNullableJson(change.getPreviousJson(), Entry.class);
            } else {
                relationship = parseJson(change.getPayloadJson(), Relationship.class);
                previousRelationship = parseNullableJson(change.getPreviousJson(), Relationship.class);
            }
            views.add(new WorkspaceChangeProposalView.ChangeView(change.getId(), change.getSortIndex(), change.getEntityType(),
                    change.getAction(), change.getTargetId(), change.getSummary(), change.getStatus(), change.getFeedback(),
                    change.getAppliedAt(), change.getCreatedAt(), change.getUpdatedAt(), workItem, previousWorkItem,
                    entry, previousEntry, relationship, previousRelationship));
        }
        return WorkspaceChangeProposalView.of(proposal, views);
    }

    private NormalizedChange createNormalizedChange(String entityType, String action, String targetId, String summary, Object payload, Object previous) {
        return new NormalizedChange(entityType, action, targetId, summary, JsonUtils.toJson(payload), previous == null ? null : JsonUtils.toJson(previous));
    }

    private String normalizeEntityType(String value) {
        String normalized = normalizeToken(value);
        if (!ENTITY_TYPES.contains(normalized))
            throw WorkItemService.createBadRequestException("Entity type must be WORK_ITEM, ENTRY, or RELATIONSHIP");
        return normalized;
    }

    private String normalizeRelationshipEntityType(String value) {
        return WorkItemService.normalizeEnumValue(value, WorkTypes.ENTITY_TYPES, "Relationship entity type");
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String resolveRef(String value, Map<String, String> refs) {
        String normalized = trimToNull(value);
        if (WorkItemService.isBlank(normalized)) throw WorkItemService.createBadRequestException("Entity reference is required");
        return refs.getOrDefault(normalized, normalized);
    }

    private String resolveNullableRef(String value, Map<String, String> refs) {
        if (value == null || value.isBlank() || "PROJECT_ROOT".equalsIgnoreCase(value.trim())) return null;
        return resolveRef(value, refs);
    }

    private String valueOrDefault(String value, String fallback) {
        return WorkItemService.isBlank(value) ? fallback : value.trim();
    }

    private String normalizeWorkItemType(String value) {
        if (WorkItemService.isBlank(value)) return "TASK";
        String normalized = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "WORK_ITEM", "WORKITEM", "ITEM" -> "TASK";
            default -> value.trim();
        };
    }

    private List<WorkItemAssignee> buildAssignees(List<AssigneeDraft> requested) {
        if (requested == null) return List.of();
        return requested.stream().map(value -> {
            if (value == null) throw WorkItemService.createBadRequestException("Assignee is required");
            WorkItemAssignee assignee = new WorkItemAssignee();
            assignee.setAssigneeType(WorkItemService.normalizeEnumValue(value.assigneeType(), WorkTypes.ASSIGNEE_TYPES, "Assignee type"));
            if (WorkItemService.isBlank(value.assigneeId())) throw WorkItemService.createBadRequestException("Assignee id is required");
            assignee.setAssigneeId(value.assigneeId().trim());
            return assignee;
        }).toList();
    }

    private String convertBlankToNull(String value) {
        return WorkItemService.isBlank(value) ? null : value.trim();
    }

    private String trimToNull(String value) {
        return value == null ? null : value.trim();
    }

    private LocalDate parseDate(String value) {
        if (WorkItemService.isBlank(value)) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception exception) {
            throw WorkItemService.createBadRequestException("Date must use YYYY-MM-DD");
        }
    }

    private <T> T parseJson(String json, Class<T> type) {
        if (WorkItemService.isBlank(json)) throw WorkItemService.createBadRequestException("Proposal payload is missing");
        return JsonUtils.fromJson(json, type);
    }

    private <T> T parseNullableJson(String json, Class<T> type) {
        return WorkItemService.isBlank(json) ? null : JsonUtils.fromJson(json, type);
    }

    private WorkItem copyWorkItem(WorkItem source) {
        WorkItem copy = new WorkItem();
        copy.setId(source.getId());
        copy.setProjectId(source.getProjectId());
        copy.setParentWorkItemId(source.getParentWorkItemId());
        copy.setSortIndex(source.getSortIndex());
        copy.setType(source.getType());
        copy.setTitle(source.getTitle());
        copy.setStatus(source.getStatus());
        copy.setDueDate(source.getDueDate());
        copy.setPriority(source.getPriority());
        copy.setCreatedByUserId(source.getCreatedByUserId());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private Entry copyEntry(Entry source) {
        Entry copy = new Entry();
        copy.setId(source.getId());
        copy.setProjectId(source.getProjectId());
        copy.setWorkItemId(source.getWorkItemId());
        copy.setSortIndex(source.getSortIndex());
        copy.setAuthorUserId(source.getAuthorUserId());
        copy.setAuthorDisplayName(source.getAuthorDisplayName());
        copy.setType(source.getType());
        copy.setBody(source.getBody());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private Relationship copyRelationship(Relationship source) {
        Relationship copy = new Relationship();
        copy.setId(source.getId());
        copy.setProjectId(source.getProjectId());
        copy.setFromEntityType(source.getFromEntityType());
        copy.setFromEntityId(source.getFromEntityId());
        copy.setToEntityType(source.getToEntityType());
        copy.setToEntityId(source.getToEntityId());
        copy.setType(source.getType());
        copy.setReason(source.getReason());
        copy.setSourceEntryId(source.getSourceEntryId());
        copy.setCreatedByUserId(source.getCreatedByUserId());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }

    private record NormalizedChange(String entityType, String action, String targetId, String summary,
                                    String payloadJson, String previousJson) {
    }
}
