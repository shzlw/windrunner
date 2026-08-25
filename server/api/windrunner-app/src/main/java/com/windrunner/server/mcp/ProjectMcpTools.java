package com.windrunner.server.mcp;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.user.domain.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Component
@RequiredArgsConstructor
public class ProjectMcpTools {

    private final ProjectRepository projectRepository;

    @McpTool(
            name = "list_projects",
            description = "List the Windrunner projects visible to the authenticated API key owner.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public List<ProjectSummary> listProjects() {
        AppUser actor = authenticatedActor();
        List<Project> projects = AppRoles.isSuperAdmin(actor.getGlobalRole())
                ? projectRepository.findAllByOrderByNameAscIdAsc()
                : projectRepository.findVisibleToUser(actor.getId());
        return projects.stream()
                .map(project -> new ProjectSummary(project.getId(), project.getName()))
                .toList();
    }

    private AppUser authenticatedActor() {
        return McpActors.authenticatedActor();
    }

    public record ProjectSummary(String id, String name) {
    }
}
