package com.windrunner.server.subscription.api;

import java.time.OffsetDateTime;

public record SubscriptionView(
        String userId,
        String projectId,
        String projectName,
        String workItemId,
        String workItemTitle,
        String workItemType,
        String parentWorkItemId,
        String parentWorkItemTitle,
        OffsetDateTime subscribedAt
) {
}