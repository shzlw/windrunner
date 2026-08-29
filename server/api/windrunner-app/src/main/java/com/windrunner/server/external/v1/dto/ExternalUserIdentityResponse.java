package com.windrunner.server.external.v1.dto;

import com.windrunner.server.user.domain.AppUser;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserIdentity", description = "Limited identity information for resolving project users.")
public record ExternalUserIdentityResponse(
        String id,
        String username,
        String displayName
) {

    public static ExternalUserIdentityResponse from(AppUser user) {
        return new ExternalUserIdentityResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName());
    }
}
