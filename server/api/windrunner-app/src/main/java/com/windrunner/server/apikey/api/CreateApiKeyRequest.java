package com.windrunner.server.apikey.api;

import java.util.List;

public record CreateApiKeyRequest(
        String name,
        List<String> scopes
) {
}
