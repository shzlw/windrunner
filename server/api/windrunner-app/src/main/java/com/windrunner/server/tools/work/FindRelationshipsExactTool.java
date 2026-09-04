package com.windrunner.server.tools.work;

import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.utils.FileUtils;
import com.windrunner.server.work.AiReviewLimits;
import com.windrunner.server.work.WorkTypes;
import com.windrunner.server.work.domain.Relationship;
import com.windrunner.server.work.persistence.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class FindRelationshipsExactTool implements Tool<FindRelationshipsExactTool.Parameters> {

    private static final int MAX_MATCHES = 100;

    private final RelationshipRepository relationships;
    private final ToolAuthorizationService authorization;

    @Override
    public String name() {
        return "find_relationships_exact";
    }

    @Override
    public boolean parallelSafe() {
        return true;
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("find-relationships-exact-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters, ToolExecutionContext context) {
        String projectId = authorization.requireProject(
                context, parameters == null ? null : parameters.projectId());
        if (parameters == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "relationship endpoints are required");
        }
        String fromType = normalizeEntityType(parameters.fromType(), "fromType");
        String fromId = required(parameters.fromId(), "fromId");
        String toType = normalizeEntityType(parameters.toType(), "toType");
        String toId = required(parameters.toId(), "toId");
        String relationshipType = normalizeRelationshipType(parameters.relationshipType());
        List<Relationship> matches = relationships.findExactPage(
                projectId, fromType, fromId, toType, toId, relationshipType, MAX_MATCHES, 0);
        long total = relationships.countExact(
                projectId, fromType, fromId, toType, toId, relationshipType);
        List<RelationshipResult> boundedMatches = matches.stream().map(RelationshipResult::from).toList();
        return new Response(boundedMatches, boundedMatches.size(), total, total > boundedMatches.size());
    }

    private String normalizeEntityType(String value, String field) {
        String normalized = required(value, field).toUpperCase(Locale.ROOT);
        if (!WorkTypes.ENTITY_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is invalid");
        }
        return normalized;
    }

    private String normalizeRelationshipType(String value) {
        String normalized = required(value, "relationshipType").toUpperCase(Locale.ROOT);
        if (!WorkTypes.RELATIONSHIP_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "relationshipType is invalid");
        }
        return normalized;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    public record Parameters(String projectId, String fromType, String fromId, String toType,
                             String toId, String relationshipType) {
    }

    public record Response(List<RelationshipResult> relationships, int count, long total, boolean hasMore) {
    }

    public record RelationshipResult(String id, String projectId, String fromType, String fromId,
                                     String toType, String toId, String type, String reason,
                                     String sourceEntryId, java.time.OffsetDateTime createdAt) {
        static RelationshipResult from(Relationship relationship) {
            return new RelationshipResult(relationship.getId(), relationship.getProjectId(),
                    relationship.getFromEntityType(), relationship.getFromEntityId(),
                    relationship.getToEntityType(), relationship.getToEntityId(), relationship.getType(),
                    AiReviewLimits.bounded(relationship.getReason(), AiReviewLimits.MAX_TEXT_LENGTH),
                    relationship.getSourceEntryId(), relationship.getCreatedAt());
        }
    }
}
