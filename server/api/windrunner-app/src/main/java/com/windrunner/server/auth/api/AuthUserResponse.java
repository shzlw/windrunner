package com.windrunner.server.auth.api;

import lombok.Builder;

@Builder
public record AuthUserResponse(
        String id,
        String username,
        String email,
        String displayName,
        String title,
        String bio,
        String timezone,
        String status,
        String globalRole,
        boolean mustChangePassword
) {
}
