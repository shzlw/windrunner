package com.windrunner.server.subscription.api;

import java.util.List;
import lombok.Builder;

@Builder
public record SubscriptionPageResponse(
        List<SubscriptionView> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}