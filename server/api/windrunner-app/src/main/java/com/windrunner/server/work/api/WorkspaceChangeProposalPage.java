package com.windrunner.server.work.api;

import java.util.List;

public record WorkspaceChangeProposalPage(
        List<WorkspaceChangeProposalSummary> proposals,
        int count,
        long total,
        int limit,
        long offset,
        boolean hasMore
) {
}
