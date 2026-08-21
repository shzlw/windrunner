package com.windrunner.server.auth.domain;

public record UserContext(
        String userId,
        String username,
        String timezone,
        String globalRole,
        String status
) {
}
