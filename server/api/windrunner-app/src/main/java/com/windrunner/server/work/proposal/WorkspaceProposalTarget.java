package com.windrunner.server.work.proposal;

public record WorkspaceProposalTarget(
        String projectId,
        String targetId,
        String summary) {
}
