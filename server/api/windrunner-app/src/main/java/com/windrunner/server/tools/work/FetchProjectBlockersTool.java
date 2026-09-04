package com.windrunner.server.tools.work;

import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.persistence.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FetchProjectBlockersTool implements Tool<FetchProjectBlockersTool.Parameters> {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final RelationshipRepository relationships;
    private final ToolAuthorizationService authorization;

    @Override
    public String name() {
        return "fetch_project_blockers";
    }

    @Override
    public boolean parallelSafe() {
        return true;
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-project-blockers-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters, ToolExecutionContext context) {
        String projectId = authorization.requireProject(
                context, parameters == null ? null : parameters.projectId());
        int limit = parameters.limit() == null ? DEFAULT_LIMIT : Math.max(1, Math.min(parameters.limit(), MAX_LIMIT));
        long offset = parameters.offset() == null ? 0 : Math.max(0, parameters.offset());
        List<Blocker> blockers = relationships.findPageWorkItemBlockers(projectId, limit, offset).stream()
                .map(row -> new Blocker(
                        row.relationshipId(),
                        row.blockedWorkItemId(),
                        row.blockedWorkItemTitle(),
                        row.blockedWorkItemStatus(),
                        row.blockerWorkItemId(),
                        row.blockerWorkItemTitle(),
                        row.blockerWorkItemStatus(),
                        row.reason()))
                .toList();
        long total = relationships.countAllWorkItemBlockers(projectId);
        return new Response(projectId, blockers.size(), total, limit, offset, offset + blockers.size() < total, blockers);
    }

    public record Parameters(String projectId, Integer limit, Integer offset) {
    }

    public record Response(String projectId, int count, long total, int limit, long offset, boolean hasMore, List<Blocker> blockers) {
    }

    public record Blocker(
            String relationshipId,
            String blockedWorkItemId,
            String blockedWorkItemTitle,
            String blockedWorkItemStatus,
            String blockerWorkItemId,
            String blockerWorkItemTitle,
            String blockerWorkItemStatus,
            String reason
    ) {
    }
}
