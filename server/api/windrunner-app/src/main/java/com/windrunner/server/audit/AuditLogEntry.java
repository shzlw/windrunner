package com.windrunner.server.audit;

public record AuditLogEntry(
        String actorUserId,
        String action,
        String entityType,
        String entityId,
        String projectId,
        String outcome,
        String summary,
        String beforeJson,
        String afterJson,
        String changesJson,
        String metadataJson
) {
}
