package com.windrunner.server.tools;

import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared authorization checks for tools. The request context is the input
 * scope; project access is revalidated against current memberships before
 * project data is loaded.
 */
@Service
@RequiredArgsConstructor
public class ToolAuthorizationService {
    private final ProjectAccessService projectAccessService;

    public String requireProject(ToolExecutionContext context, String projectId) {
        ToolExecutionContext checkedContext = requireContext(context);
        String normalizedProjectId = checkedContext.requireProjectId(projectId);
        projectAccessService.requireProjectRole(
                normalizedProjectId, checkedContext.actor(), ProjectRoles.VIEWER);
        return normalizedProjectId;
    }

    public ToolExecutionContext requireContext(ToolExecutionContext context) {
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tool execution context is required");
        }
        return context;
    }
}
