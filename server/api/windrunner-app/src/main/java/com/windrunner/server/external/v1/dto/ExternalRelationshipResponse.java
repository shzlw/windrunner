package com.windrunner.server.external.v1.dto;

import com.windrunner.server.work.domain.Relationship;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "Relationship", description = "A typed, directional link between two entities.")
public record ExternalRelationshipResponse(
        String id,
        String projectId,
        String fromEntityType,
        String fromEntityId,
        String toEntityType,
        String toEntityId,
        String type,
        String reason,
        String sourceEntryId,
        String createdByUserId,
        OffsetDateTime createdAt
) {
    public static ExternalRelationshipResponse from(Relationship relationship) {
        return new ExternalRelationshipResponse(
                relationship.getId(),
                relationship.getProjectId(),
                relationship.getFromEntityType(),
                relationship.getFromEntityId(),
                relationship.getToEntityType(),
                relationship.getToEntityId(),
                relationship.getType(),
                relationship.getReason(),
                relationship.getSourceEntryId(),
                relationship.getCreatedByUserId(),
                relationship.getCreatedAt());
    }
}
