package com.windrunner.server.work;

import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.utils.JsonUtils;
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

    private final WorkspaceChangeProposalRepository proposals;
    private final WorkspaceChangeRepository changes;
    private final WorkItemService workItems;
    private final EntryService entries;
    private final RelationshipService relationships;
    private final EntityIdGenerator ids;

    @Transactional
    public WorkspaceChangeProposalView create(String projectId, String chatSessionId, String sourceMessageId,
                                              String sourceText, ProposalDraft draft) {
        if (draft == null || draft.changes() == null || draft.changes().isEmpty()) {
            throw WorkItemService.bad("At least one workspace change is required");
        }
        if (draft.changes().size() > 100) throw WorkItemService.bad("A proposal can contain at most 100 changes");

        String proposalId = ids.generate(EntityIdType.WORKSPACE_CHANGE_PROPOSAL);
        proposals.insert(proposalId, projectId, chatSessionId, sourceMessageId, sourceText);

        Map<String, String> reservedIds = reserveIds(draft.changes());
        int sortIndex = 0;
        for (ChangeDraft requested : draft.changes()) {
            NormalizedChange normalized = normalize(projectId, requested, reservedIds);
            changes.insert(ids.generate(EntityIdType.WORKSPACE_CHANGE), proposalId, projectId, sortIndex++,
                    normalized.entityType(), normalized.action(), normalized.targetId(), normalized.summary(),
                    normalized.payloadJson(), normalized.previousJson());
        }
        return get(projectId, proposalId);
    }

    public List<WorkspaceChangeProposalView> list(String projectId) {
        return proposals.findByProjectId(projectId).stream().map(proposal -> view(proposal, changes.findByProposalId(proposal.getId()))).toList();
    }

    public WorkspaceChangeProposalView get(String projectId, String proposalId) {
        WorkspaceChangeProposal proposal = proposals.findInProject(proposalId, projectId)
                .orElseThrow(() -> WorkItemService.notFound("Workspace proposal not found"));
        return view(proposal, changes.findByProposalId(proposalId));
    }

    @Transactional
    public WorkspaceChangeProposalView decide(String projectId, String proposalId, String changeId,
                                              DecisionRequest request, String actorId) {
        if (request == null || WorkItemService.blank(request.decision()))
            throw WorkItemService.bad("Proposal decision is required");
        WorkspaceChange change = changes.findInProposal(changeId, proposalId, projectId)
                .orElseThrow(() -> WorkItemService.notFound("Workspace proposal change not found"));
        if (!Set.of("PENDING", "NEEDS_UPDATE").contains(change.getStatus())) {
            throw WorkItemService.bad("This proposal change has already been decided");
        }

        String decision = request.decision().trim().toUpperCase();
        String status;
        if ("ACCEPT".equals(decision)) {
            apply(projectId, change, actorId);
            status = "APPLIED";
        } else if ("REJECT".equals(decision)) {
            status = "REJECTED";
        } else if ("REQUEST_UPDATE".equals(decision)) {
            if (WorkItemService.blank(request.feedback()))
                throw WorkItemService.bad("Feedback is required when requesting an update");
            status = "NEEDS_UPDATE";
        } else {
            throw WorkItemService.bad("Proposal decision must be ACCEPT, REJECT, or REQUEST_UPDATE");
        }
        changes.decide(changeId, proposalId, projectId, status,
                WorkItemService.blank(request.feedback()) ? null : request.feedback().trim());
        refreshProposalStatus(projectId, proposalId);
        return get(projectId, proposalId);
    }

    private void apply(String projectId, WorkspaceChange change, String actorId) {
        switch (change.getEntityType()) {
            case "WORK_ITEM" -> applyWorkItem(projectId, change, actorId);
            case "ENTRY" -> applyEntry(projectId, change, actorId);
            case "RELATIONSHIP" -> applyRelationship(projectId, change, actorId);
            default -> throw WorkItemService.bad("Unsupported proposal entity type");
        }
    }

    private void applyWorkItem(String projectId, WorkspaceChange change, String actorId) {
        WorkItemPayload payload = read(change.getPayloadJson(), WorkItemPayload.class);
        if ("ADD".equals(change.getAction())) {
            workItems.createWithId(projectId, change.getTargetId(), payload.workItem(), payload.assignees(), actorId);
        } else if ("UPDATE".equals(change.getAction())) {
            requireUnchangedWorkItem(projectId, change);
            workItems.update(projectId, change.getTargetId(), payload.workItem(), payload.assignees(), actorId);
        } else {
            requireUnchangedWorkItem(projectId, change);
            workItems.delete(projectId, change.getTargetId(), actorId);
        }
    }

    private void applyEntry(String projectId, WorkspaceChange change, String actorId) {
        Entry payload = read(change.getPayloadJson(), Entry.class);
        if ("ADD".equals(change.getAction())) entries.createWithId(projectId, change.getTargetId(), payload, actorId);
        else if ("UPDATE".equals(change.getAction())) {
            requireUnchangedEntry(projectId, change);
            entries.update(projectId, change.getTargetId(), payload, actorId);
        } else {
            requireUnchangedEntry(projectId, change);
            entries.delete(projectId, change.getTargetId(), actorId);
        }
    }

    private void applyRelationship(String projectId, WorkspaceChange change, String actorId) {
        Relationship payload = read(change.getPayloadJson(), Relationship.class);
        if ("ADD".equals(change.getAction()))
            relationships.createWithId(projectId, change.getTargetId(), payload, actorId);
        else if ("UPDATE".equals(change.getAction())) {
            requireUnchangedRelationship(projectId, change);
            relationships.updateReason(projectId, change.getTargetId(), payload.getReason(), actorId);
        } else {
            requireUnchangedRelationship(projectId, change);
            relationships.delete(projectId, change.getTargetId(), actorId);
        }
    }

    private void requireUnchangedWorkItem(String projectId, WorkspaceChange change) {
        WorkItemPayload before = read(change.getPreviousJson(), WorkItemPayload.class);
        WorkItem current = workItems.get(projectId, change.getTargetId());
        if (!Objects.equals(current.getUpdatedAt(), before.workItem().getUpdatedAt())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Work item changed after this AI suggestion was created. Ask AI to review it again.");
        }
    }

    private void requireUnchangedEntry(String projectId, WorkspaceChange change) {
        Entry before = read(change.getPreviousJson(), Entry.class);
        Entry current = entries.get(projectId, change.getTargetId());
        if (!Objects.equals(current.getUpdatedAt(), before.getUpdatedAt())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Entry changed after this AI suggestion was created. Ask AI to review it again.");
        }
    }

    private void requireUnchangedRelationship(String projectId, WorkspaceChange change) {
        Relationship before = read(change.getPreviousJson(), Relationship.class);
        Relationship current = relationships.list(projectId).stream().filter(candidate -> change.getTargetId().equals(candidate.getId())).findFirst()
                .orElseThrow(() -> WorkItemService.notFound("Relationship not found"));
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
            if (WorkItemService.blank(change.clientRef())) throw WorkItemService.bad("New records require a clientRef");
            String reserved = switch (entityType) {
                case "WORK_ITEM" -> ids.generate(EntityIdType.WORK_ITEM);
                case "ENTRY" -> ids.generate(EntityIdType.ENTRY);
                case "RELATIONSHIP" -> ids.generate(EntityIdType.RELATIONSHIP);
                default -> throw WorkItemService.bad("Unsupported proposal entity type");
            };
            if (result.putIfAbsent(change.clientRef().trim(), reserved) != null) {
                throw WorkItemService.bad("Proposal clientRef values must be unique");
            }
        }
        return result;
    }

    private NormalizedChange normalize(String projectId, ChangeDraft change, Map<String, String> reservedIds) {
        if (change == null) throw WorkItemService.bad("Workspace change is required");
        String entityType = normalizeEntityType(change.entityType());
        String action = normalizeToken(change.action());
        if (!ACTIONS.contains(action)) throw WorkItemService.bad("Change action must be ADD, UPDATE, or DELETE");
        String targetId = "ADD".equals(action) ? reservedIds.get(trim(change.clientRef())) : trim(change.targetId());
        if (WorkItemService.blank(targetId)) throw WorkItemService.bad("Existing records require a targetId");
        String summary = WorkItemService.blank(change.summary()) ? action + " " + entityType.toLowerCase().replace('_', ' ') : change.summary().trim();

        return switch (entityType) {
            case "WORK_ITEM" -> normalizeWorkItem(projectId, action, targetId, summary, change.workItem(), reservedIds);
            case "ENTRY" -> normalizeEntry(projectId, action, targetId, summary, change.entry(), reservedIds);
            case "RELATIONSHIP" ->
                    normalizeRelationship(projectId, action, targetId, summary, change.relationship(), reservedIds);
            default -> throw WorkItemService.bad("Unsupported proposal entity type");
        };
    }

    private NormalizedChange normalizeWorkItem(String projectId, String action, String targetId, String summary,
                                               WorkItemDraft draft, Map<String, String> reservedIds) {
        WorkItemPayload previous = null;
        WorkItem item;
        List<WorkItemAssignee> assignees;
        if ("ADD".equals(action)) {
            if (draft == null || WorkItemService.blank(draft.title()))
                throw WorkItemService.bad("New work items require a title");
            item = new WorkItem();
            item.setId(targetId);
            item.setProjectId(projectId);
            item.setTitle(draft.title().trim());
            item.setType(workItemTypeOrDefault(draft.type()));
            item.setStatus(valueOr(draft.status(), "OPEN"));
            item.setDueDate(date(draft.dueDate()));
            item.setPriority(blankToNull(draft.priority()));
            item.setParentWorkItemId(resolveNullableRef(draft.parentWorkItemId(), reservedIds));
            assignees = assignees(draft.assignees());
        } else {
            WorkItem current = workItems.get(projectId, targetId);
            List<WorkItemAssignee> currentAssignees = workItems.assignees(targetId);
            previous = new WorkItemPayload(current, currentAssignees);
            item = copy(current);
            assignees = currentAssignees;
            if (!"DELETE".equals(action)) {
                if (draft == null) throw WorkItemService.bad("Work item updates require proposed values");
                if (!WorkItemService.blank(draft.title())) item.setTitle(draft.title().trim());
                if (!WorkItemService.blank(draft.type())) item.setType(workItemTypeOrDefault(draft.type()));
                if (!WorkItemService.blank(draft.status())) item.setStatus(draft.status());
                if (draft.dueDate() != null) item.setDueDate(date(draft.dueDate()));
                if (draft.priority() != null) item.setPriority(blankToNull(draft.priority()));
                if (draft.parentWorkItemId() != null)
                    item.setParentWorkItemId(resolveNullableRef(draft.parentWorkItemId(), reservedIds));
                if (draft.assignees() != null) assignees = assignees(draft.assignees());
            }
        }
        WorkItemService.enumValue(item.getType(), WorkTypes.WORK_ITEM_TYPES, "Work item type");
        WorkItemService.enumValue(item.getStatus(), WorkTypes.WORK_ITEM_STATUSES, "Work item status");
        WorkItemPayload payload = new WorkItemPayload(item, assignees);
        return normalized("WORK_ITEM", action, targetId, summary, payload, previous);
    }

    private NormalizedChange normalizeEntry(String projectId, String action, String targetId, String summary,
                                            EntryDraft draft, Map<String, String> reservedIds) {
        Entry previous = null;
        Entry entry = new Entry();
        if ("ADD".equals(action)) {
            if (draft == null || WorkItemService.blank(draft.body()) || WorkItemService.blank(draft.workItemId())) {
                throw WorkItemService.bad("New entries require workItemId and body");
            }
            entry.setId(targetId);
            entry.setProjectId(projectId);
            entry.setWorkItemId(resolveRef(draft.workItemId(), reservedIds));
            entry.setType(valueOr(draft.type(), "COMMENT"));
            entry.setBody(draft.body().trim());
        } else {
            previous = entries.get(projectId, targetId);
            entry = copy(previous);
            if (!"DELETE".equals(action)) {
                if (draft == null) throw WorkItemService.bad("Entry updates require proposed values");
                if (!WorkItemService.blank(draft.type())) entry.setType(draft.type());
                if (!WorkItemService.blank(draft.body())) entry.setBody(draft.body().trim());
            }
        }
        WorkItemService.enumValue(entry.getType(), WorkTypes.ENTRY_TYPES, "Entry type");
        return normalized("ENTRY", action, targetId, summary, entry, previous);
    }

    private NormalizedChange normalizeRelationship(String projectId, String action, String targetId, String summary,
                                                   RelationshipDraft draft, Map<String, String> reservedIds) {
        Relationship previous = null;
        Relationship relationship = new Relationship();
        if ("ADD".equals(action)) {
            if (draft == null) throw WorkItemService.bad("New relationships require proposed values");
            relationship.setId(targetId);
            relationship.setProjectId(projectId);
            relationship.setFromEntityType(normalizeRelationshipEntityType(draft.fromEntityType()));
            relationship.setFromEntityId(resolveRef(draft.fromEntityId(), reservedIds));
            relationship.setToEntityType(normalizeRelationshipEntityType(draft.toEntityType()));
            relationship.setToEntityId(resolveRef(draft.toEntityId(), reservedIds));
            relationship.setType(WorkItemService.enumValue(draft.type(), WorkTypes.RELATIONSHIP_TYPES, "Relationship type"));
            relationship.setReason(blankToNull(draft.reason()));
            relationship.setSourceEntryId(resolveNullableRef(draft.sourceEntryId(), reservedIds));
        } else {
            previous = relationships.list(projectId).stream().filter(candidate -> targetId.equals(candidate.getId())).findFirst()
                    .orElseThrow(() -> WorkItemService.notFound("Relationship not found"));
            relationship = copy(previous);
            if ("UPDATE".equals(action) && draft != null && draft.reason() != null) relationship.setReason(blankToNull(draft.reason()));
        }
        return normalized("RELATIONSHIP", action, targetId, summary, relationship, previous);
    }

    private void refreshProposalStatus(String projectId, String proposalId) {
        List<WorkspaceChange> all = changes.findByProposalId(proposalId);
        String status = all.stream().anyMatch(change -> Set.of("PENDING", "NEEDS_UPDATE").contains(change.getStatus())) ? "PENDING" : "COMPLETED";
        proposals.updateStatus(proposalId, projectId, status);
    }

    private WorkspaceChangeProposalView view(WorkspaceChangeProposal proposal, List<WorkspaceChange> storedChanges) {
        List<WorkspaceChangeProposalView.ChangeView> views = new ArrayList<>();
        for (WorkspaceChange change : storedChanges) {
            WorkItemView workItem = null;
            WorkItemView previousWorkItem = null;
            Entry entry = null;
            Entry previousEntry = null;
            Relationship relationship = null;
            Relationship previousRelationship = null;
            if ("WORK_ITEM".equals(change.getEntityType())) {
                WorkItemPayload payload = read(change.getPayloadJson(), WorkItemPayload.class);
                WorkItemPayload before = readNullable(change.getPreviousJson(), WorkItemPayload.class);
                workItem = new WorkItemView(payload.workItem(), payload.assignees());
                if (before != null) previousWorkItem = new WorkItemView(before.workItem(), before.assignees());
            } else if ("ENTRY".equals(change.getEntityType())) {
                entry = read(change.getPayloadJson(), Entry.class);
                previousEntry = readNullable(change.getPreviousJson(), Entry.class);
            } else {
                relationship = read(change.getPayloadJson(), Relationship.class);
                previousRelationship = readNullable(change.getPreviousJson(), Relationship.class);
            }
            views.add(new WorkspaceChangeProposalView.ChangeView(change.getId(), change.getSortIndex(), change.getEntityType(),
                    change.getAction(), change.getTargetId(), change.getSummary(), change.getStatus(), change.getFeedback(),
                    change.getAppliedAt(), change.getCreatedAt(), change.getUpdatedAt(), workItem, previousWorkItem,
                    entry, previousEntry, relationship, previousRelationship));
        }
        return WorkspaceChangeProposalView.of(proposal, views);
    }

    private NormalizedChange normalized(String entityType, String action, String targetId, String summary, Object payload, Object previous) {
        return new NormalizedChange(entityType, action, targetId, summary, JsonUtils.toJson(payload), previous == null ? null : JsonUtils.toJson(previous));
    }

    private String normalizeEntityType(String value) {
        String normalized = normalizeToken(value);
        if (!ENTITY_TYPES.contains(normalized))
            throw WorkItemService.bad("Entity type must be WORK_ITEM, ENTRY, or RELATIONSHIP");
        return normalized;
    }

    private String normalizeRelationshipEntityType(String value) {
        return WorkItemService.enumValue(value, WorkTypes.ENTITY_TYPES, "Relationship entity type");
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String resolveRef(String value, Map<String, String> refs) {
        String normalized = trim(value);
        if (WorkItemService.blank(normalized)) throw WorkItemService.bad("Entity reference is required");
        return refs.getOrDefault(normalized, normalized);
    }

    private String resolveNullableRef(String value, Map<String, String> refs) {
        if (value == null || value.isBlank() || "PROJECT_ROOT".equalsIgnoreCase(value.trim())) return null;
        return resolveRef(value, refs);
    }

    private String valueOr(String value, String fallback) {
        return WorkItemService.blank(value) ? fallback : value.trim();
    }

    private String workItemTypeOrDefault(String value) {
        if (WorkItemService.blank(value)) return "TASK";
        String normalized = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "WORK_ITEM", "WORKITEM", "ITEM" -> "TASK";
            default -> value.trim();
        };
    }

    private List<WorkItemAssignee> assignees(List<AssigneeDraft> requested) {
        if (requested == null) return List.of();
        return requested.stream().map(value -> {
            if (value == null) throw WorkItemService.bad("Assignee is required");
            WorkItemAssignee assignee = new WorkItemAssignee();
            assignee.setAssigneeType(WorkItemService.enumValue(value.assigneeType(), WorkTypes.ASSIGNEE_TYPES, "Assignee type"));
            if (WorkItemService.blank(value.assigneeId())) throw WorkItemService.bad("Assignee id is required");
            assignee.setAssigneeId(value.assigneeId().trim());
            return assignee;
        }).toList();
    }

    private String blankToNull(String value) {
        return WorkItemService.blank(value) ? null : value.trim();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private LocalDate date(String value) {
        if (WorkItemService.blank(value)) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception exception) {
            throw WorkItemService.bad("Date must use YYYY-MM-DD");
        }
    }

    private <T> T read(String json, Class<T> type) {
        if (WorkItemService.blank(json)) throw WorkItemService.bad("Proposal payload is missing");
        return JsonUtils.fromJson(json, type);
    }

    private <T> T readNullable(String json, Class<T> type) {
        return WorkItemService.blank(json) ? null : JsonUtils.fromJson(json, type);
    }

    private WorkItem copy(WorkItem source) {
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

    private Entry copy(Entry source) {
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

    private Relationship copy(Relationship source) {
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

    public record ProposalDraft(List<ChangeDraft> changes) {
    }

    public record ChangeDraft(String entityType, String action, String targetId, String clientRef, String summary,
                              WorkItemDraft workItem, EntryDraft entry, RelationshipDraft relationship) {
    }

    public record WorkItemDraft(String title, String type, String status, String dueDate, String priority,
                                String parentWorkItemId, List<AssigneeDraft> assignees) {
    }

    public record AssigneeDraft(String assigneeType, String assigneeId) {
    }

    public record EntryDraft(String workItemId, String type, String body) {
    }

    public record RelationshipDraft(String fromEntityType, String fromEntityId, String toEntityType, String toEntityId,
                                    String type, String reason, String sourceEntryId) {
    }

    public record DecisionRequest(String decision, String feedback) {
    }

    public record WorkItemPayload(WorkItem workItem, List<WorkItemAssignee> assignees) {
    }

    private record NormalizedChange(String entityType, String action, String targetId, String summary,
                                    String payloadJson, String previousJson) {
    }
}
