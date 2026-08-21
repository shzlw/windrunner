package com.windrunner.server.subscription.api;

import lombok.Builder;

import java.util.List;

@Builder
public record SubscriptionPageResponse(
        List<SubscriptionView> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}