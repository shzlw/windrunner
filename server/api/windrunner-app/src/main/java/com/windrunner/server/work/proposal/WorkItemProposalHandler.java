package com.windrunner.server.work.proposal;

import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.proposal.ProposalHandler;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.utils.JsonUtils;
import com.windrunner.server.work.WorkItemService;
import com.windrunner.server.work.WorkTypes;
import com.windrunner.server.work.api.AssigneeDraft;
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
