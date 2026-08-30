package com.windrunner.server.mcp;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.user.domain.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectMcpTools {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final ProjectRepository projectRepository;
    private final McpAuthorization authorization;

    @McpTool(
            name = "list_projects",
            description = "List one bounded page of projects visible to the API key owner. The response includes total and hasMore; use limit and offset to fetch another page only when needed.",
            generateOutputSchema = true,
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public ProjectPage listProjects(Integer limit, Long offset) {
        AppUser actor = authorization.requireScope(ApiKeyScopes.PROJECTS_READ);
        int normalizedLimit = limit == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(limit, MAX_LIMIT));
        long normalizedOffset = offset == null ? 0 : Math.max(0, offset);
        List<Project> projects = AppRoles.isSuperAdmin(actor.getGlobalRole())
                ? projectRepository.findAllPage(normalizedLimit, normalizedOffset)
                : projectRepository.findVisibleToUserPaged(actor.getId(), normalizedLimit, normalizedOffset);
        long total = AppRoles.isSuperAdmin(actor.getGlobalRole())
                ? projectRepository.countProjects()
                : projectRepository.countVisibleToUser(actor.getId());
        List<ProjectSummary> summaries = projects.stream()
                .map(project -> new ProjectSummary(project.getId(), project.getName()))
                .toList();
        return new ProjectPage(summaries, summaries.size(), total, normalizedLimit, normalizedOffset,
                normalizedOffset + summaries.size() < total);
    }

    public record ProjectSummary(String id, String name) {
    }

    public record ProjectPage(
            List<ProjectSummary> projects,
            int count,
            long total,
            int limit,
            long offset,
            boolean hasMore) {
    }
}
