package com.windrunner.server.user.api;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record UserResponse(
        String id,
        String username,
        String email,
        String displayName,
        String timezone,
        String status,
        String globalRole,
        boolean mustChangePassword,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
