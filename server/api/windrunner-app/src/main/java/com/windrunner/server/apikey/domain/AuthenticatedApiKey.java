package com.windrunner.server.apikey.domain;

import com.windrunner.server.user.domain.AppUser;
import java.util.List;

public record AuthenticatedApiKey(
        ApiKey apiKey,
        AppUser owner,
        List<String> scopes
) {
}
