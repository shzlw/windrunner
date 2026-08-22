package com.windrunner.server.external.v1.dto;

import com.windrunner.server.work.domain.Entry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "Entry", description = "A chronological record attached to a work item.")
public record ExternalEntryResponse(
        String id,
        String projectId,
        String workItemId,
        Integer sortIndex,
        String authorUserId,
        String type,
        String body,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ExternalEntryResponse from(Entry entry) {
        return new ExternalEntryResponse(
                entry.getId(),
                entry.getProjectId(),
                entry.getWorkItemId(),
                entry.getSortIndex(),
                entry.getAuthorUserId(),
                entry.getType(),
                entry.getBody(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
