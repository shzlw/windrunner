package com.windrunner.server.user.api;

import lombok.Builder;

import java.util.List;

@Builder
public record UserPageResponse(
        List<UserResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
