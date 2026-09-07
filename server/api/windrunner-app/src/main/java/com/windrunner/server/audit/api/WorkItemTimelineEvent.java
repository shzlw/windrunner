package com.windrunner.server.audit.api;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkItemTimelineEvent(
        String id,
        String kind,
        OffsetDateTime occurredAt,
        String actorUserId,
        String fromStatus,
        String toStatus,
        String fromDueDate,
        String toDueDate,
        List<AssigneeChange> addedAssignees,
        List<AssigneeChange> removedAssignees
) {

    public record AssigneeChange(
            String assigneeType,
            String assigneeId
    ) {
    }
}
