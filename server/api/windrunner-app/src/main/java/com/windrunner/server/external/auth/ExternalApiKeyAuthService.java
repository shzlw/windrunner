package com.windrunner.server.external.auth;

import com.windrunner.server.apikey.ApiKeyService;
import com.windrunner.server.apikey.domain.AuthenticatedApiKey;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class ExternalApiKeyAuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyService apiKeyService;

    public AuthenticatedApiKey requireScope(HttpServletRequest request, String requiredScope) {
        AuthenticatedApiKey authenticatedApiKey = apiKeyService.authenticate(readBearerToken(request));
        if (!authenticatedApiKey.scopes().contains(requiredScope)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "API key scope is not allowed");
        }
        return authenticatedApiKey;
    }

    private String readBearerToken(HttpServletRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key is required");
        }

        String authorizationHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer API key is required");
        }
        return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
    }
}
