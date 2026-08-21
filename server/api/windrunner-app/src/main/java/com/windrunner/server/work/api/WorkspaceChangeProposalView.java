package com.windrunner.server.work.api;

import com.windrunner.server.work.domain.Entry;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.domain.WorkspaceChangeProposal;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkspaceChangeProposalView(
        String id,
        String projectId,
        String chatSessionId,
        String sourceMessageId,
        String sourceText,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<ChangeView> changes
) {
    public static WorkspaceChangeProposalView of(WorkspaceChangeProposal proposal, List<ChangeView> changes) {
        return new WorkspaceChangeProposalView(proposal.getId(), proposal.getProjectId(), proposal.getChatSessionId(),
                proposal.getSourceMessageId(), proposal.getSourceText(), proposal.getStatus(), proposal.getCreatedAt(),
                proposal.getUpdatedAt(), changes);
    }

    public record ChangeView(
            String id,
            int sortIndex,
            String entityType,
            String action,
            String targetId,
            String summary,
            String status,
            String feedback,
            OffsetDateTime appliedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            WorkItemView workItem,
            WorkItemView previousWorkItem,
            Entry entry,
            Entry previousEntry,
            Relationship relationship,
            Relationship previousRelationship
    ) {
    }
}
