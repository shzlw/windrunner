package com.windrunner.server.work.proposal;

import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.proposal.ProposalChange;
import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.WorkTypes;
import com.windrunner.server.work.api.AssigneeDraft;
import com.windrunner.server.work.api.ChangeDraft;
import com.windrunner.server.work.api.WorkItemDraft;
import com.windrunner.server.work.api.WorkItemPayload;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
final class WorkItemProposalHandler implements ProposalHandler<WorkspaceProposalChange, WorkspacePreparedChange> {
    private final WorkItemService workItemService;
    private final ProjectAccessService projectAccessService;

    @Override
    public String getEntityType() {
        return "WORK_ITEM";
    }

    @Override
    public void authorize(WorkspaceProposalChange change, AppUser actor) {
        projectAccessService.requireProjectRole(change.projectId(), actor, ProjectRoles.EDITOR);
    }

    @Override
    public WorkspacePreparedChange prepare(WorkspaceProposalChange change, AppUser actor) {
        WorkItemDraft draft = change.draft() == null ? null : change.draft().workItem();
        WorkItemPayload previous = null;
        WorkItem item;
        List<WorkItemAssignee> assignees;

        if ("ADD".equals(change.action())) {
            if (draft == null || isBlank(draft.title())) {
                throw createBadRequestException("New work items require a title");
            }
            item = new WorkItem();
            item.setId(change.targetId());
            item.setProjectId(change.projectId());
            item.setTitle(draft.title().trim());
            item.setType(normalizeWorkItemType(draft.type()));
            item.setStatus(normalizeEnum(valueOrDefault(draft.status(), "OPEN"), WorkTypes.WORK_ITEM_STATUSES,
                    "Work item status"));
            item.setDueDate(parseDate(draft.dueDate()));
            item.setPriority(convertBlankToNull(draft.priority()));
            item.setParentWorkItemId(resolveNullableRef(draft.parentWorkItemId(), change.reservedIds()));
            assignees = buildAssignees(draft.assignees());
        } else {
            WorkItem current = workItemService.get(change.projectId(), change.targetId());
            List<WorkItemAssignee> currentAssignees = workItemService.findAssignees(change.targetId());
            previous = new WorkItemPayload(current, currentAssignees);
            item = copyWorkItem(current);
            assignees = currentAssignees;
            if (!"DELETE".equals(change.action())) {
                if (draft == null) {
                    throw createBadRequestException("Work item updates require proposed values");
                }
                if (!isBlank(draft.title())) item.setTitle(draft.title().trim());
                if (!isBlank(draft.type())) item.setType(normalizeWorkItemType(draft.type()));
                if (!isBlank(draft.status())) {
                    item.setStatus(normalizeEnum(draft.status(), WorkTypes.WORK_ITEM_STATUSES, "Work item status"));
                }
                if (draft.dueDate() != null) item.setDueDate(parseDate(draft.dueDate()));
                if (draft.priority() != null) item.setPriority(convertBlankToNull(draft.priority()));
                if (draft.parentWorkItemId() != null) {
                    item.setParentWorkItemId(resolveNullableRef(draft.parentWorkItemId(), change.reservedIds()));
                }
                if (draft.assignees() != null) assignees = buildAssignees(draft.assignees());
            }
        }

        item.setType(normalizeEnum(item.getType(), WorkTypes.WORK_ITEM_TYPES, "Work item type"));
        WorkItemPayload payload = new WorkItemPayload(item, assignees);
        return new WorkspacePreparedChange(
                JsonUtils.toJson(payload),
                previous == null ? null : JsonUtils.toJson(previous),
                createBaseVersion(previous == null ? null : previous.workItem().getUpdatedAt()));
    }

    @Override
    public void apply(WorkspaceProposalChange change, WorkspacePreparedChange prepared, AppUser actor) {
        WorkItemPayload payload = JsonUtils.fromJson(prepared.payloadJson(), WorkItemPayload.class);
        if ("ADD".equals(change.action())) {
            workItemService.createWithId(change.projectId(), change.targetId(), payload.workItem(), payload.assignees(), actor.getId());
            return;
        }

        WorkspaceProposalVersion version = JsonUtils.fromJson(
                prepared.baseVersionJson(), WorkspaceProposalVersion.class);
        WorkItem current = workItemService.get(change.projectId(), change.targetId());
        if (!Objects.equals(current.getUpdatedAt(), version.updatedAt())) {
            throw createConflictException("Work item changed after this AI suggestion was created. Ask AI to review it again.");
        }
        if ("UPDATE".equals(change.action())) {
            workItemService.update(change.projectId(), change.targetId(), payload.workItem(), payload.assignees(), actor.getId());
        } else {
            workItemService.delete(change.projectId(), change.targetId(), actor.getId());
        }
    }

    @Override
    public WorkspaceProposalChange buildRevert(ProposalChange appliedChange, AppUser actor) {
        if (!"UPDATE".equals(appliedChange.getOperation())) {
            throw createBadRequestException("Work item creation or deletion cannot be safely reverted because dependent data is not fully captured");
        }
        WorkspaceProposalTarget target = JsonUtils.fromJson(appliedChange.getTargetRef(), WorkspaceProposalTarget.class);
        WorkItemPayload before = JsonUtils.fromJson(appliedChange.getBeforeSnapshot(), WorkItemPayload.class);
        WorkItemPayload after = JsonUtils.fromJson(appliedChange.getAfterSnapshot(), WorkItemPayload.class);
        WorkItem current = workItemService.get(target.projectId(), target.targetId());
        List<WorkItemAssignee> currentAssignees = workItemService.findAssignees(target.targetId());

        String title = buildRevertValue("work item title", current.getTitle(), before.workItem().getTitle(),
                after.workItem().getTitle());
        String type = buildRevertValue("work item type", current.getType(), before.workItem().getType(),
                after.workItem().getType());
        String status = buildRevertValue("work item status", current.getStatus(), before.workItem().getStatus(),
                after.workItem().getStatus());
        String dueDate = buildNullableRevertValue("work item due date", current.getDueDate(),
                before.workItem().getDueDate(), after.workItem().getDueDate());
        String priority = buildNullableRevertValue("work item priority", current.getPriority(),
                before.workItem().getPriority(), after.workItem().getPriority());
        String parentWorkItemId = buildParentRevertValue(current.getParentWorkItemId(),
                before.workItem().getParentWorkItemId(), after.workItem().getParentWorkItemId());
        List<AssigneeDraft> assignees = buildAssigneeRevert(currentAssignees, before.assignees(), after.assignees());

        if (title == null && type == null && status == null && dueDate == null && priority == null
                && parentWorkItemId == null && assignees == null) {
            throw createBadRequestException("This work item proposal has no reversible changes");
        }
        WorkItemDraft draft = new WorkItemDraft(title, type, status, dueDate, priority, parentWorkItemId, assignees);
        ChangeDraft changeDraft = new ChangeDraft("WORK_ITEM", "UPDATE", target.targetId(), null,
                "Revert " + target.summary(), draft, null, null);
        return new WorkspaceProposalChange(target.projectId(), "UPDATE", target.targetId(),
                changeDraft.summary(), changeDraft, Map.of());
    }

    private String buildRevertValue(String label, String current, String before, String after) {
        if (Objects.equals(before, after)) return null;
        requireCurrentValue(label, current, after);
        return before;
    }

    private String buildNullableRevertValue(String label, Object current, Object before, Object after) {
        if (Objects.equals(before, after)) return null;
        requireCurrentValue(label, current, after);
        return before == null ? "" : before.toString();
    }

    private String buildParentRevertValue(String current, String before, String after) {
        if (Objects.equals(before, after)) return null;
        requireCurrentValue("work item parent", current, after);
        return before == null ? "PROJECT_ROOT" : before;
    }

    private List<AssigneeDraft> buildAssigneeRevert(List<WorkItemAssignee> current,
                                                    List<WorkItemAssignee> before,
                                                    List<WorkItemAssignee> after) {
        if (assigneeKeys(before).equals(assigneeKeys(after))) return null;
        if (!assigneeKeys(current).equals(assigneeKeys(after))) {
            throw createConflictException("Cannot safely revert because work item assignees changed after the proposal was applied. "
                    + "Create a new proposal from the current assignees if you still want to restore them");
        }
        return before.stream()
                .map(assignee -> new AssigneeDraft(assignee.getAssigneeType(), assignee.getAssigneeId()))
                .toList();
    }

    private List<String> assigneeKeys(List<WorkItemAssignee> assignees) {
        List<String> keys = new ArrayList<>();
        if (assignees != null) {
            assignees.forEach(assignee -> keys.add(assignee.getAssigneeType() + ":" + assignee.getAssigneeId()));
        }
        keys.sort(Comparator.naturalOrder());
        return keys;
    }

    private void requireCurrentValue(String label, Object current, Object expected) {
        if (!Objects.equals(current, expected)) {
            throw createConflictException("Cannot safely revert because " + label + " changed after the proposal was applied. "
                    + "Create a new proposal from the current value if you still want to restore it");
        }
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

    private List<WorkItemAssignee> buildAssignees(List<AssigneeDraft> requested) {
        if (requested == null) return List.of();
        return requested.stream().map(value -> {
            if (value == null) throw createBadRequestException("Assignee is required");
            WorkItemAssignee assignee = new WorkItemAssignee();
            assignee.setAssigneeType(normalizeEnum(value.assigneeType(), WorkTypes.ASSIGNEE_TYPES, "Assignee type"));
            if (isBlank(value.assigneeId())) throw createBadRequestException("Assignee id is required");
            assignee.setAssigneeId(value.assigneeId().trim());
            return assignee;
        }).toList();
    }

    private String resolveNullableRef(String value, Map<String, String> reservedIds) {
        if (isBlank(value) || "PROJECT_ROOT".equalsIgnoreCase(value.trim())) return null;
        String normalized = value.trim();
        return reservedIds.getOrDefault(normalized, normalized);
    }

    private String normalizeWorkItemType(String value) {
        if (isBlank(value)) return "TASK";
        String normalized = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if (List.of("WORK_ITEM", "WORKITEM", "ITEM").contains(normalized)) return "TASK";
        return normalizeEnum(normalized, WorkTypes.WORK_ITEM_TYPES, "Work item type");
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            throw createBadRequestException("Date must use YYYY-MM-DD");
        }
    }

    private String createBaseVersion(OffsetDateTime updatedAt) {
        return JsonUtils.toJson(new WorkspaceProposalVersion(updatedAt));
    }

    private String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String convertBlankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String normalizeEnum(String value, Set<String> allowed, String name) {
        String normalized = value == null ? null : value.trim().toUpperCase();
        if (normalized == null || !allowed.contains(normalized)) {
            throw createBadRequestException(name + " is invalid");
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException createBadRequestException(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException createConflictException(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
