package com.windrunner.server.work.api;

public record EntryAiReviewResponse(String originalBody, String proposedBody, String proposedType, String rationale) {
}
