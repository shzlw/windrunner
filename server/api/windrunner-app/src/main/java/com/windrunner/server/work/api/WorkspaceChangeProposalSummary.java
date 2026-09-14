package com.windrunner.server.work.api;

import com.windrunner.server.proposal.Proposal;

import java.time.OffsetDateTime;

public record WorkspaceChangeProposalSummary(
        String id,
        String projectId,
        String sourceText,
        String revertsProposalId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static WorkspaceChangeProposalSummary of(Proposal proposal) {
        return new WorkspaceChangeProposalSummary(
                proposal.getId(),
                proposal.getProjectId(),
                proposal.getSourceText(),
                proposal.getRevertsProposalId(),
                proposal.getStatus(),
                proposal.getCreatedAt(),
                proposal.getUpdatedAt());
    }
}
