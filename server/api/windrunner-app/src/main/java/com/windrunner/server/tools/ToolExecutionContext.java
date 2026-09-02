package com.windrunner.server.tools;

import com.windrunner.server.user.domain.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Request-scoped authorization and correlation data for one tool invocation.
 *
 * <p>The context is created by the request boundary. Tools must use it for
 * actor and project checks rather than accepting identity or project scope
 * from model-generated arguments.</p>
 */
public record ToolExecutionContext(
        AppUser actor,
        String chatSessionId,
        List<String> allowedProjectIds) {

    public ToolExecutionContext {
        Objects.requireNonNull(actor, "actor is required");
        chatSessionId = chatSessionId == null || chatSessionId.isBlank()
                ? null
                : chatSessionId.trim();
        allowedProjectIds = allowedProjectIds == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(allowedProjectIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .toList()));
    }

    public String requireProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        String normalizedProjectId = projectId.trim();
        if (!allowedProjectIds.contains(normalizedProjectId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The requested project is not in the selected context");
        }
        return normalizedProjectId;
    }
}
