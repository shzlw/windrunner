package com.windrunner.server.tools.work;

import com.windrunner.server.tools.Tool;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.domain.Relationship;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FetchRelationshipsTool implements Tool<FetchRelationshipsTool.Parameters> {
    private final RelationshipService relationships;

    @Override
    public String name() {
        return "fetch_relationships";
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-relationships-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters) {
        if (parameters == null || parameters.projectId() == null || parameters.projectId().isBlank())
            throw new IllegalArgumentException("projectId is required");
        String entityId = parameters.entityId() == null ? null : parameters.entityId().trim();
        int limit = parameters.limit() == null ? 50 : Math.max(1, Math.min(parameters.limit(), 100));
        long offset = parameters.offset() == null ? 0 : Math.max(0, parameters.offset());
        List<Relationship> results = relationships.listPageForTool(parameters.projectId().trim(), entityId, limit, offset);
        long total = relationships.countForTool(parameters.projectId().trim(), entityId);
        return new Response(results, results.size(), total, limit, offset, offset + results.size() < total);
    }

    public record Parameters(String projectId, String entityId, Integer limit, Integer offset) {
    }

    public record Response(List<Relationship> relationships, int count, long total, int limit, long offset, boolean hasMore) {
    }
}
