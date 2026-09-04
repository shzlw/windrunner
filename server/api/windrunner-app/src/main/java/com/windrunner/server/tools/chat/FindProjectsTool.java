package com.windrunner.server.tools.chat;

import com.windrunner.server.auth.security.AppRoles;
import com.windrunner.server.llm.LlmTool;
import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class FindProjectsTool {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_QUERY_LENGTH = 200;

    private final ProjectRepository projects;

    public LlmTool<Parameters> forContext(ToolExecutionContext context) {
        Objects.requireNonNull(context, "Tool execution context is required");
        return new LlmTool<>(
                "find_projects",
                FileUtils.loadSystemPrompt("find-projects-tool.md"),
                Parameters.class,
                parameters -> {
                    String query = normalizeQuery(parameters == null ? null : parameters.query());
                    int limit = normalizeLimit(parameters == null ? null : parameters.limit());
                    List<Project> matches = AppRoles.isSuperAdmin(context.actor().getGlobalRole())
                            ? projects.findAllByQuery(query, limit)
                            : projects.findVisibleToUserByQuery(context.actor().getId(), query, limit);
                    return new Result(
                            matches.stream().map(project -> new ProjectMatch(project.getId(), project.getName())).toList(),
                            matches.size(),
                            limit);
                },
                true);
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) return null;
        String trimmed = query.trim();
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH) : trimmed;
    }

    private int normalizeLimit(Integer requestedLimit) {
        return requestedLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
    }

    public record Parameters(String query, Integer limit) { }

    public record Result(List<ProjectMatch> projects, int count, int limit) { }

    public record ProjectMatch(String id, String name) { }
}
