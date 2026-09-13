package com.windrunner.server.work.proposal;

import com.windrunner.server.work.api.ChangeDraft;

import java.util.Map;

public record WorkspaceProposalChange(
        String projectId,
        String action,
        String targetId,
        String summary,
        ChangeDraft draft,
        Map<String, String> reservedIds) {
}
