package com.windrunner.server.work;

import com.windrunner.server.audit.*;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.notification.NotificationService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.persistence.ProjectMemberRepository;
import com.windrunner.server.team.persistence.ProjectTeamRepository;
import com.windrunner.server.team.persistence.TeamMemberRepository;
import com.windrunner.server.work.api.WorkItemMoveRequest;
import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import com.windrunner.server.work.persistence.EntryRepository;
import com.windrunner.server.work.persistence.RelationshipRepository;
import com.windrunner.server.work.persistence.WorkItemAssigneeRepository;
import com.windrunner.server.work.persistence.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkItemService {
    private final WorkItemRepository workItems;
    private final WorkItemAssigneeRepository assignees;
    private final ProjectMemberRepository projectMembers;
    private final ProjectTeamRepository projectTeams;
    private final TeamMemberRepository teamMembers;
    private final EntryRepository entries;
    private final RelationshipRepository relationships;
    private final ContentOrderService contentOrder;
    private final EntityIdGenerator ids;
    private final AuditLogService auditLogService;
    private final com.windrunner.server.search.SearchNormalizer searchNormalizer;
    private final com.windrunner.server.subscription.persistence.SubscriptionRepository subscriptions;
    private final NotificationService notificationService;
    private final com.windrunner.server.notification.WorkItemNotificationAudience notificationAudience;

    public List<WorkItem> list(String projectId) {
        return workItems.findByProjectId(projectId);
    }

    public WorkItem get(String projectId, String id) {
        return require(projectId, id);
    }

    @Transactional
    public WorkItem create(String projectId, WorkItem item, List<WorkItemAssignee> requestedAssignees, String actorId) {
        return createWithId(projectId, ids.generate(EntityIdType.WORK_ITEM), item, requestedAssignees, actorId);
    }

    @Transactional
    public WorkItem createWithId(String projectId, String id, WorkItem item, List<WorkItemAssignee> requestedAssignees, String actorId) {
        if (blank(id)) throw bad("Work item id is required");
        item.setProjectId(projectId);
        item.setCreatedByUserId(actorId);
        item.setId(id);
        normalize(item, null);
        workItems.insert(item.getId(), projectId, item.getParentWorkItemId(), item.getSortIndex(), item.getType(), item.getTitle(), item.getStatus(), item.getDueDate(), item.getPriority(), actorId, searchNormalizer.normalize(item.getTitle()));
        Set<String> newlyAssignedUserIds = replaceAssignees(projectId, item.getId(), requestedAssignees);
        notificationService.notifyWorkItemAssigned(newlyAssignedUserIds, actorId, projectId, item.getId(), item.getTitle());
        WorkItem created = get(projectId, item.getId());
        auditLogService.logAfterCommit(audit(actorId, AuditActions.CREATE, created, null, snapshot(created)));
        return created;
    }

    @Transactional
    public WorkItem update(String projectId, String id, WorkItem item, List<WorkItemAssignee> requestedAssignees, String actorId) {
        WorkItem current = requireForUpdate(projectId, id);
        Map<String, Object> before = snapshot(current);
        item.setProjectId(projectId);
        item.setId(id);
        normalize(item, current);
        boolean parentChanged = !Objects.equals(current.getParentWorkItemId(), item.getParentWorkItemId());
        if (parentChanged) {
            contentOrder.moveWorkItem(projectId, id, current.getParentWorkItemId(), item.getParentWorkItemId(), null, null);
            item.setSortIndex(require(projectId, id).getSortIndex());
        } else item.setSortIndex(current.getSortIndex());
        if (workItems.update(id, projectId, item.getParentWorkItemId(), item.getSortIndex(), item.getType(), item.getTitle(), item.getStatus(), item.getDueDate(), item.getPriority(), searchNormalizer.normalize(item.getTitle())) == 0)
            throw notFound("Work item not found");
        if (requestedAssignees != null) {
            Set<String> newlyAssignedUserIds = replaceAssignees(projectId, id, requestedAssignees);
            notificationService.notifyWorkItemAssigned(newlyAssignedUserIds, actorId, projectId, id, item.getTitle());
        }
        WorkItem updated = get(projectId, id);
        if (!Objects.equals(current.getStatus(), updated.getStatus())) {
            notificationService.notifyWorkItemActivity(
                    notificationAudience.resolve(id, actorId),
                    actorId,
                    projectId,
                    id,
                    updated.getTitle(),
                    List.of("changed status " + current.getStatus() + " → " + updated.getStatus()));
        }
        auditLogService.logAfterCommit(audit(actorId, AuditActions.UPDATE, updated, before, snapshot(updated)));
        return updated;
    }

    /**
     * Status-only change; keeps every other field as-is. Used by automation
     * surfaces (MCP) so partial writes cannot clobber other fields.
     */
    @Transactional
    public WorkItem updateStatus(String projectId, String id, String status, String actorId) {
        WorkItem current = get(projectId, id);
        WorkItem change = new WorkItem();
        change.setParentWorkItemId(current.getParentWorkItemId());
        change.setSortIndex(current.getSortIndex());
        change.setType(current.getType());
        change.setTitle(current.getTitle());
        change.setStatus(status);
        change.setDueDate(current.getDueDate());
        change.setPriority(current.getPriority());
        return update(projectId, id, change, null, actorId);
    }

    @Transactional
    public WorkItem move(String projectId, String id, WorkItemMoveRequest request, String actorId) {
        WorkItem current = require(projectId, id);
        Map<String, Object> before = snapshot(current);
        String destinationParentId = request == null || blank(request.parentWorkItemId()) ? null : request.parentWorkItemId();
        if (destinationParentId != null) {
            if (id.equals(destinationParentId)) throw bad("A WorkItem cannot be its own parent");
            require(projectId, destinationParentId);
            ensureNoCycle(projectId, id, destinationParentId);
        }
        contentOrder.moveWorkItem(projectId, id, current.getParentWorkItemId(), destinationParentId,
                request == null ? null : request.beforeEntityType(), request == null ? null : request.beforeEntityId());
        WorkItem updated = get(projectId, id);
        auditLogService.logAfterCommit(audit(actorId, AuditActions.UPDATE, updated, before, snapshot(updated)));
        return updated;
    }

    @Transactional
    public void delete(String projectId, String id, String actorId) {
        WorkItem current = get(projectId, id);
        Map<String, Object> before = snapshot(current);
        deleteSubtree(projectId, id, workItems.findByProjectId(projectId));
        auditLogService.logAfterCommit(audit(actorId, AuditActions.DELETE, current, before, null));
    }

    private void deleteSubtree(String projectId, String id, List<WorkItem> projectItems) {
        projectItems.stream()
                .filter(item -> id.equals(item.getParentWorkItemId()))
                .forEach(child -> deleteSubtree(projectId, child.getId(), projectItems));
        for (var entry : entries.findByWorkItemId(id)) relationships.deleteForEntity(projectId, "ENTRY", entry.getId());
        relationships.deleteForEntity(projectId, "WORK_ITEM", id);
        entries.deleteByWorkItemId(projectId, id);
        assignees.deleteByWorkItemId(id);
        workItems.deleteInProject(id, projectId);
    }

    public List<WorkItemAssignee> assignees(String workItemId) {
        return assignees.findByWorkItemId(workItemId);
    }

    private void normalize(WorkItem item, WorkItem current) {
        if (item == null || blank(item.getTitle())) throw bad("Work item title is required");
        item.setType(enumValue(item.getType() == null ? "TASK" : item.getType(), WorkTypes.WORK_ITEM_TYPES, "Work item type"));
        item.setStatus(enumValue(item.getStatus() == null ? "OPEN" : item.getStatus(), WorkTypes.WORK_ITEM_STATUSES, "Work item status"));
        item.setTitle(item.getTitle().trim());
        item.setPriority(blank(item.getPriority()) ? null : item.getPriority().trim().toUpperCase());
        if (item.getParentWorkItemId() != null && item.getParentWorkItemId().isBlank()) item.setParentWorkItemId(null);
        if (item.getParentWorkItemId() != null) {
            if (item.getId() != null && item.getId().equals(item.getParentWorkItemId()))
                throw bad("A WorkItem cannot be its own parent");
            require(item.getProjectId(), item.getParentWorkItemId());
            ensureNoCycle(item.getProjectId(), item.getId(), item.getParentWorkItemId());
        }
        if (item.getSortIndex() == null)
            item.setSortIndex(current == null ? contentOrder.nextSortIndex(item.getProjectId(), item.getParentWorkItemId()) : current.getSortIndex());
    }

    private Set<String> replaceAssignees(String projectId, String workItemId, List<WorkItemAssignee> requested) {
        Set<String> previouslyAssignedUserIds = effectiveUserIds(assignees.findByWorkItemId(workItemId));
        assignees.deleteByWorkItemId(workItemId);
        Set<String> seen = new HashSet<>();
        if (requested != null) {
            for (WorkItemAssignee assignee : requested) {
                String type = enumValue(assignee.getAssigneeType(), WorkTypes.ASSIGNEE_TYPES, "Assignee type");
                if (blank(assignee.getAssigneeId())) throw bad("Assignee id is required");
                String assigneeId = assignee.getAssigneeId().trim();
                String key = type + ":" + assigneeId;
                if (!seen.add(key)) continue;
                boolean valid = "USER".equals(type)
                        ? projectMembers.findByProjectIdAndUserId(projectId, assigneeId).isPresent()
                        || projectMembers.hasTeamRole(projectId, assigneeId, List.of(ProjectRoles.OWNER, ProjectRoles.EDITOR, ProjectRoles.VIEWER))
                        : projectTeams.findByProjectIdAndTeamId(projectId, assigneeId).isPresent();
                if (!valid) throw bad("Assignee must belong to the project");
                assignees.insert(ids.generate(EntityIdType.WORK_ITEM_ASSIGNEE), workItemId, type, assigneeId);
                if ("USER".equals(type)) {
                    subscriptions.insert(ids.generate(EntityIdType.WORK_ITEM_SUBSCRIPTION), assigneeId, projectId, workItemId);
                }
            }
        }
        Set<String> newlyAssignedUserIds = effectiveUserIds(assignees.findByWorkItemId(workItemId));
        newlyAssignedUserIds.removeAll(previouslyAssignedUserIds);
        return newlyAssignedUserIds;
    }

    private Set<String> effectiveUserIds(List<WorkItemAssignee> assigned) {
        Set<String> userIds = new LinkedHashSet<>();
        Set<String> teamIds = new LinkedHashSet<>();
        for (WorkItemAssignee assignee : assigned) {
            if ("USER".equalsIgnoreCase(assignee.getAssigneeType())) {
                userIds.add(assignee.getAssigneeId());
            } else if ("TEAM".equalsIgnoreCase(assignee.getAssigneeType())) {
                teamIds.add(assignee.getAssigneeId());
            }
        }
        if (!teamIds.isEmpty()) {
            teamMembers.findByTeamIds(new ArrayList<>(teamIds)).forEach(member -> userIds.add(member.getUserId()));
        }
        return userIds;
    }

    private void ensureNoCycle(String projectId, String id, String parentId) {
        if (id == null) return;
        String cursor = parentId;
        while (cursor != null) {
            if (id.equals(cursor)) throw bad("Work item hierarchy cannot contain a cycle");
            cursor = require(projectId, cursor).getParentWorkItemId();
        }
    }

    private WorkItem require(String projectId, String id) {
        return workItems.findById(id).filter(x -> projectId.equals(x.getProjectId())).orElseThrow(() -> notFound("Work item not found"));
    }

    private WorkItem requireForUpdate(String projectId, String id) {
        return workItems.findByIdForUpdate(id).filter(x -> projectId.equals(x.getProjectId())).orElseThrow(() -> notFound("Work item not found"));
    }

    private Map<String, Object> snapshot(WorkItem item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", item.getId());
        snapshot.put("parentWorkItemId", item.getParentWorkItemId());
        snapshot.put("sortIndex", item.getSortIndex());
        snapshot.put("type", item.getType());
        snapshot.put("title", item.getTitle());
        snapshot.put("status", item.getStatus());
        snapshot.put("dueDate", item.getDueDate());
        snapshot.put("priority", item.getPriority());
        snapshot.put("assignees", assignees(item.getId()));
        return snapshot;
    }

    private AuditLogEntry audit(String actorId, String action, WorkItem item, Map<String, Object> before, Map<String, Object> after) {
        return new AuditLogEntry(actorId, action, AuditEntityTypes.WORK_ITEM, item.getId(), item.getProjectId(), AuditOutcomes.SUCCESS, action + " work item " + item.getTitle(), auditLogService.json(before), auditLogService.json(after), auditLogService.changes(before, after), null);
    }

    static String enumValue(String value, Set<String> allowed, String name) {
        String v = value == null ? null : value.trim().toUpperCase();
        if (v == null || !allowed.contains(v)) throw bad(name + " is invalid");
        return v;
    }

    static boolean blank(String v) {
        return v == null || v.isBlank();
    }

    static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    static ResponseStatusException notFound(String m) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, m);
    }
}
