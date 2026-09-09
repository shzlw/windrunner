package com.windrunner.server.audit;

import com.windrunner.server.audit.api.WorkItemTimelineEvent;
import com.windrunner.server.audit.persistence.AuditLogRepository;
import com.windrunner.server.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class WorkItemTimelineService {

    private static final int MAX_EVENTS = 200;
    private static final Set<String> PAUSED_STATUSES = Set.of("BLOCKED", "WAITING");
    private static final Set<String> COMPLETED_STATUSES = Set.of("DONE", "ANSWERED", "APPROVED");
    private static final Set<String> CLOSED_STATUSES = Set.of("REJECTED", "CANCELLED");

    private final AuditLogRepository auditLogRepository;

    public List<WorkItemTimelineEvent> list(String projectId, String workItemId) {
        return auditLogRepository.findWorkItemTimeline(projectId, workItemId, MAX_EVENTS).stream()
                .flatMap(row -> toEvents(row).stream())
                .toList();
    }

    public List<WorkItemTimelineEvent> listLatest(String projectId, String workItemId, int limit) {
        List<WorkItemTimelineEvent> events = list(projectId, workItemId);
        int safeLimit = Math.max(1, Math.min(limit, 20));
        int fromIndex = Math.max(0, events.size() - safeLimit);
        List<WorkItemTimelineEvent> latest = new ArrayList<>(events.subList(fromIndex, events.size()));
        return List.copyOf(latest.reversed());
    }

    private List<WorkItemTimelineEvent> toEvents(AuditLogRepository.WorkItemTimelineRow row) {
        Map<String, Object> before = parseObject(row.beforeJson());
        Map<String, Object> after = parseObject(row.afterJson());
        Map<String, Object> changes = parseObject(row.changesJson());
        List<WorkItemTimelineEvent> events = new ArrayList<>();

        if ("CREATE".equals(row.action())) {
            events.add(new WorkItemTimelineEvent(
                    row.id(), "CREATED", row.occurredAt(), row.actorUserId(), null,
                    stringValue(after.get("status")), null, stringValue(after.get("dueDate")),
                    readAssignees(after), List.of()));
            return events;
        }

        if ("DELETE".equals(row.action())) {
            events.add(new WorkItemTimelineEvent(
                    row.id(), "DELETED", row.occurredAt(), row.actorUserId(), null, null,
                    null, null, List.of(), List.of()));
            return events;
        }

        if (hasChange(changes, "status")) {
            String fromStatus = changeValue(changes, "status", "from");
            String toStatus = changeValue(changes, "status", "to");
            events.add(new WorkItemTimelineEvent(
                    row.id() + ":status", classifyStatusChange(fromStatus, toStatus), row.occurredAt(),
                    row.actorUserId(), fromStatus, toStatus, null, null, List.of(), List.of()));
        }

        if (hasChange(changes, "assignees")) {
            AssigneeDiff diff = diffAssignees(before, after);
            if (!diff.added().isEmpty() || !diff.removed().isEmpty()) {
                events.add(new WorkItemTimelineEvent(
                        row.id() + ":assignees", "ASSIGNMENT_CHANGED", row.occurredAt(), row.actorUserId(),
                        null, null, null, null, diff.added(), diff.removed()));
            }
        }

        if (hasChange(changes, "dueDate")) {
            events.add(new WorkItemTimelineEvent(
                    row.id() + ":due-date", "DUE_DATE_CHANGED", row.occurredAt(), row.actorUserId(),
                    null, null, changeValue(changes, "dueDate", "from"), changeValue(changes, "dueDate", "to"),
                    List.of(), List.of()));
        }

        return events;
    }

    private String classifyStatusChange(String fromStatus, String toStatus) {
        if ("IN_PROGRESS".equals(toStatus)) {
            return PAUSED_STATUSES.contains(fromStatus) ? "RESUMED" : "STARTED";
        }
        if (PAUSED_STATUSES.contains(toStatus)) {
            return "PAUSED";
        }
        if (COMPLETED_STATUSES.contains(toStatus)) {
            return "COMPLETED";
        }
        if (CLOSED_STATUSES.contains(toStatus)) {
            return "CLOSED";
        }
        if (COMPLETED_STATUSES.contains(fromStatus) || CLOSED_STATUSES.contains(fromStatus)) {
            return "REOPENED";
        }
        return "STATUS_CHANGED";
    }

    private AssigneeDiff diffAssignees(Map<String, Object> before, Map<String, Object> after) {
        Map<String, WorkItemTimelineEvent.AssigneeChange> beforeAssignees = indexAssignees(before);
        Map<String, WorkItemTimelineEvent.AssigneeChange> afterAssignees = indexAssignees(after);
        List<WorkItemTimelineEvent.AssigneeChange> added = afterAssignees.entrySet().stream()
                .filter(entry -> !beforeAssignees.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        List<WorkItemTimelineEvent.AssigneeChange> removed = beforeAssignees.entrySet().stream()
                .filter(entry -> !afterAssignees.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        return new AssigneeDiff(added, removed);
    }

    private Map<String, WorkItemTimelineEvent.AssigneeChange> indexAssignees(Map<String, Object> snapshot) {
        Map<String, WorkItemTimelineEvent.AssigneeChange> result = new LinkedHashMap<>();
        Object rawAssignees = snapshot.get("assignees");
        if (!(rawAssignees instanceof List<?> assignees)) {
            return result;
        }
        for (Object rawAssignee : assignees) {
            if (!(rawAssignee instanceof Map<?, ?> assignee)) {
                continue;
            }
            String type = stringValue(assignee.get("assigneeType"));
            String id = stringValue(assignee.get("assigneeId"));
            if (StringUtils.hasText(type) && StringUtils.hasText(id)) {
                WorkItemTimelineEvent.AssigneeChange value = new WorkItemTimelineEvent.AssigneeChange(type, id);
                result.put(type + ":" + id, value);
            }
        }
        return result;
    }

    private List<WorkItemTimelineEvent.AssigneeChange> readAssignees(Map<String, Object> snapshot) {
        return List.copyOf(indexAssignees(snapshot).values());
    }

    private boolean hasChange(Map<String, Object> changes, String field) {
        return changes.containsKey(field);
    }

    private String changeValue(Map<String, Object> changes, String field, String direction) {
        Object rawChange = changes.get(field);
        if (rawChange instanceof Map<?, ?> change) {
            return stringValue(change.get(direction));
        }
        return null;
    }

    private Map<String, Object> parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return JsonUtils.fromJson(json, Map.class);
        } catch (IllegalArgumentException exception) {
            return Map.of();
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record AssigneeDiff(
            List<WorkItemTimelineEvent.AssigneeChange> added,
            List<WorkItemTimelineEvent.AssigneeChange> removed
    ) {
    }
}
