package com.windrunner.server.work.api;

import com.windrunner.server.work.domain.WorkItemAssignee;

import java.util.List;

public record WorkItemAiReviewResponse(String originalTitle, String proposedTitle, String proposedType,
                                       String proposedStatus,
                                       String proposedDueDate, String proposedPriority,
                                       List<WorkItemAssignee> proposedAssignees,
                                       List<ProposedBlocker> proposedBlockers, String rationale) {
    public record ProposedBlocker(String workItemId, String reason) {
    }
}
