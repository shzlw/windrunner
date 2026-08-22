package com.windrunner.server.external.v1.dto;

import com.windrunner.server.work.domain.WorkItem;
import com.windrunner.server.work.domain.WorkItemAssignee;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(name = "WorkItem", description = "A work item with its assignees.")
public record ExternalWorkItemResponse(
        @Schema(description = "The work item.") WorkItemPayload workItem,
        @Schema(description = "Users and teams responsible for this item.") List<Assignee> assignees
) {

    @Schema(name = "WorkItemPayload", description = "The work item fields.")
    public record WorkItemPayload(
            String id,
            String projectId,
            String parentWorkItemId,
            Integer sortIndex,
            String type,
            String title,
            String status,
            LocalDate dueDate,
            String priority,
            String createdByUserId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static WorkItemPayload from(WorkItem item) {
            return new WorkItemPayload(
                    item.getId(),
                    item.getProjectId(),
                    item.getParentWorkItemId(),
                    item.getSortIndex(),
                    item.getType(),
                    item.getTitle(),
                    item.getStatus(),
                    item.getDueDate(),
                    item.getPriority(),
                    item.getCreatedByUserId(),
                    item.getCreatedAt(),
                    item.getUpdatedAt());
        }
    }

    @Schema(name = "WorkItemAssignee", description = "A user or team assigned to the work item.")
    public record Assignee(
            String assigneeType,
            String assigneeId
    ) {
        public static Assignee from(WorkItemAssignee assignee) {
            return new Assignee(assignee.getAssigneeType(), assignee.getAssigneeId());
        }
    }

    public static ExternalWorkItemResponse from(WorkItem item, List<WorkItemAssignee> assignees) {
        return new ExternalWorkItemResponse(
                WorkItemPayload.from(item),
                assignees.stream().map(Assignee::from).toList());
    }
}
