package com.windrunner.server.apikey.api;

import com.windrunner.server.apikey.domain.ApiKey;
import java.time.OffsetDateTime;
import java.util.List;

public record ApiKeyResponse(
        String id,
        String ownerUserId,
        String name,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt,
        List<String> scopes
) {
    public static ApiKeyResponse from(ApiKey apiKey, List<String> scopes) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getOwnerUserId(),
                apiKey.getName(),
                apiKey.getStatus(),
                apiKey.getCreatedAt(),
                apiKey.getLastUsedAt(),
                apiKey.getRevokedAt(),
                List.copyOf(scopes)
        );
    }
}
