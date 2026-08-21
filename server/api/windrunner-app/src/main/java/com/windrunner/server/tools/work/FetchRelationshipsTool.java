package com.windrunner.server.tools.work;

import com.windrunner.server.tools.Tool;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.RelationshipService;
import com.windrunner.server.work.domain.Relationship;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class FetchRelationshipsTool implements Tool<FetchRelationshipsTool.Parameters> {
    private final RelationshipService relationships;
    @Override public String name() { return "fetch_relationships"; }
    @Override public String description() { return FileUtils.loadSystemPrompt("fetch-relationships-tool.md"); }
    @Override public Class<Parameters> parametersType() { return Parameters.class; }
    @Override public Object execute(Parameters parameters) {
        if (parameters == null || parameters.projectId() == null || parameters.projectId().isBlank()) throw new IllegalArgumentException("projectId is required");
        String entityId = parameters.entityId(); int limit = parameters.limit() == null ? 50 : Math.max(1, Math.min(parameters.limit(), 100));
        List<Relationship> results = relationships.list(parameters.projectId()).stream().filter(r -> entityId == null || entityId.isBlank() || entityId.equals(r.getFromEntityId()) || entityId.equals(r.getToEntityId())).limit(limit).toList();
        return new Response(results, results.size(), limit);
    }
    public record Parameters(String projectId, String entityId, Integer limit) { }
    public record Response(List<Relationship> relationships, int count, int limit) { }
}
