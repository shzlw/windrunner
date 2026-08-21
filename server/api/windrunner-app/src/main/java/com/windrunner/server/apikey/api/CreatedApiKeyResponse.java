package com.windrunner.server.apikey.api;

import java.time.OffsetDateTime;
import java.util.List;

public record CreatedApiKeyResponse(
        String id,
        String ownerUserId,
        String name,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt,
        List<String> scopes,
        String rawKey
) {
}
