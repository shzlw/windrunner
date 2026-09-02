package com.windrunner.server.mcp;

import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.project.ProjectAccessService;
import com.windrunner.server.project.ProjectRoles;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.user.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class McpAuthorization {

    private final ExternalAccessService externalAccessService;
    private final ProjectAccessService projectAccessService;
    private final ProjectRepository projectRepository;

    public AppUser requireScope(String scope) {
        return requireScopes(scope);
    }

    public AppUser requireScopes(String... scopes) {
        return externalAccessService.requireScopes(currentRequest(), scopes);
    }

    public AppUser requireProjectViewer(String projectId, String... scopes) {
        String normalizedProjectId = requireProjectId(projectId);
        AppUser actor = requireScopes(scopes);
        projectAccessService.requireProjectRole(normalizedProjectId, actor, ProjectRoles.VIEWER);
        return actor;
    }

    public ToolExecutionContext toolContext(AppUser actor) {
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        List<String> projectIds = AppRoles.isSuperAdmin(actor.getGlobalRole())
                ? projectRepository.findAllByOrderByNameAscIdAsc().stream().map(project -> project.getId()).toList()
                : projectRepository.findVisibleToUser(actor.getId()).stream().map(project -> project.getId()).toList();
        return new ToolExecutionContext(actor, null, projectIds);
    }

    public ToolExecutionContext toolContext(AppUser actor, String projectId) {
        return new ToolExecutionContext(actor, null, List.of(requireProjectId(projectId)));
    }

    public String requireProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        return projectId.trim();
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MCP API key is required");
        }
        return attributes.getRequest();
    }
}
