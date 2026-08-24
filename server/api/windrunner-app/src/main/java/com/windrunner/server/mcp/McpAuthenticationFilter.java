package com.windrunner.server.mcp;

import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalApiKeyAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class McpAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACTOR_REQUEST_ATTRIBUTE = McpAuthenticationFilter.class.getName() + ".actor";

    private static final String MCP_PATH = "/mcp";

    private final ExternalApiKeyAuthService apiKeyAuthService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !MCP_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            var authenticatedApiKey = apiKeyAuthService.requireScope(request, ApiKeyScopes.PROJECTS_READ);
            request.setAttribute(ACTOR_REQUEST_ATTRIBUTE, authenticatedApiKey.owner());
            filterChain.doFilter(request, response);
        } catch (ResponseStatusException exception) {
            response.sendError(exception.getStatusCode().value(), exception.getReason());
        }
    }
}
