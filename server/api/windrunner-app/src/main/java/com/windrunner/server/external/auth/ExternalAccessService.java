package com.windrunner.server.external.auth;

import com.windrunner.server.apikey.domain.AuthenticatedApiKey;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class ExternalAccessService {

    private final ExternalApiKeyAuthService apiKeyAuthService;

    public AppUser requireScope(HttpServletRequest request, String scope) {
        return requireScopes(request, scope);
    }

    public AppUser requireScopes(HttpServletRequest request, String... scopes) {
        AuthenticatedApiKey authenticatedApiKey = apiKeyAuthService.authenticate(request);
        if (scopes != null) {
            for (String scope : scopes) {
                if (!authenticatedApiKey.scopes().contains(scope)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "API key scope is not allowed");
                }
            }
        }
        return authenticatedApiKey.owner();
    }

    public AppUser requireAdminScope(HttpServletRequest request, String scope) {
        AppUser owner = requireScope(request, scope);
        if (!AppRoles.isAdminLike(owner.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required");
        }
        return owner;
    }
}
