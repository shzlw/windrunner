package com.windrunner.server.work.api;

public record RelationshipDraft(
        String fromEntityType,
        String fromEntityId,
        String toEntityType,
        String toEntityId,
        String type,
        String reason,
        String sourceEntryId
) {
}
