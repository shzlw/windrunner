package com.windrunner.server.work.api;

public record ChangeDraft(
        String entityType,
        String action,
        String targetId,
        String clientRef,
        String summary,
        WorkItemDraft workItem,
        EntryDraft entry,
        RelationshipDraft relationship
) {
}
