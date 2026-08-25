package com.windrunner.server.mcp;

import com.windrunner.server.user.domain.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the authenticated API-key owner for MCP tool invocations. The
 * {@link McpAuthenticationFilter} stores the owner as a request attribute;
 * tools read it through this helper.
 */
public final class McpActors {

    private McpActors() {
    }

    public static AppUser authenticatedActor() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MCP API key is required");
        }
        Object actor = attributes.getRequest().getAttribute(McpAuthenticationFilter.ACTOR_REQUEST_ATTRIBUTE);
        if (!(actor instanceof AppUser appUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MCP API key is required");
        }
        return appUser;
    }
}
