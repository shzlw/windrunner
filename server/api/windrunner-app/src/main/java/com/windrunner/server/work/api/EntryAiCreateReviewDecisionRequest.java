package com.windrunner.server.work.api;

public record EntryAiCreateReviewDecisionRequest(
        String workItemId,
        String type,
        String originalBody,
        String proposedBody
) {
}
