package com.windrunner.server.work.api;

public record EntryAiNewReviewRequest(String workItemId, String type, String body, String instruction) {
}
