package com.windrunner.server.tools;

import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.auth.AuthService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.user.domain.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared authorization checks for tools. The request context is the input
 * scope; project access is revalidated against current memberships before
 * project data is loaded.
 */
@Service
public class ToolAuthorizationService {
    private final ProjectAccessService projectAccessService;
    private final AuthService authService;

    public ToolAuthorizationService(ProjectAccessService projectAccessService, AuthService authService) {
        this.projectAccessService = projectAccessService;
        this.authService = authService;
    }

    public String requireProject(ToolExecutionContext context, String projectId) {
        ToolExecutionContext checkedContext = requireContext(context);
        String normalizedProjectId = checkedContext.requireProjectId(projectId);
        projectAccessService.requireProjectRole(
                normalizedProjectId, requireActor(checkedContext), ProjectRoles.VIEWER);
        return normalizedProjectId;
    }

    public AppUser requireActor(ToolExecutionContext context) {
        return authService.requireActiveActor(requireContext(context).actor());
    }

    public AppUser requireAdmin(ToolExecutionContext context) {
        AppUser actor = requireActor(context);
        if (!AppRoles.isAdminLike(actor.getGlobalRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required");
        }
        return actor;
    }

    public AppUser requireProjectOwner(ToolExecutionContext context, String projectId) {
        String normalizedProjectId = requireContext(context).requireProjectId(projectId);
        AppUser actor = requireActor(context);
        projectAccessService.requireProjectRole(normalizedProjectId, actor, ProjectRoles.OWNER);
        return actor;
    }

    public ToolExecutionContext requireContext(ToolExecutionContext context) {
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tool execution context is required");
        }
        return context;
    }
}
