package com.windrunner.server.work.api;

public record EntryAiReviewDecisionRequest(String originalBody, String proposedBody, String type) {
}
