package com.windrunner.server.analytics.api;

import java.util.List;

public record AiAnalyticsSummary(
        long questionsAsked,
        long questionsAnswered,
        long proposalsCreated,
        long changesProposed,
        long changesAccepted,
        long changesRejected,
        long changesNeedsUpdate,
        long changesPending,
        List<EntitySummary> byEntity
) {
    public record EntitySummary(
            String entityType,
            long changesProposed,
            long changesAccepted,
            long changesRejected,
            long changesNeedsUpdate,
            long changesPending
    ) {
    }
}
