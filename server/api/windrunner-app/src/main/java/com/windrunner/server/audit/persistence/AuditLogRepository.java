package com.windrunner.server.audit.persistence;

import com.windrunner.server.audit.domain.AuditLog;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends CrudRepository<AuditLog, String> {

    record WorkItemStatusRow(
            String workItemId,
            OffsetDateTime occurredAt,
            String status
    ) {
    }

    record WorkItemTimelineRow(
            String id,
            OffsetDateTime occurredAt,
            String actorUserId,
            String action,
            String beforeJson,
            String afterJson,
            String changesJson
    ) {
    }

    @Query("""
            SELECT entity_id AS work_item_id,
                   occurred_at,
                   after_json ->> 'status' AS status
            FROM audit_log
            WHERE entity_type = 'WORK_ITEM'
              AND entity_id IN (:workItemIds)
              AND (action = 'CREATE' OR changes_json -> 'status' IS NOT NULL)
            ORDER BY entity_id, occurred_at, id
            """)
    List<WorkItemStatusRow> findWorkItemStatusHistory(@Param("workItemIds") List<String> workItemIds);

    @Query("""
            SELECT id, occurred_at, actor_user_id, action, before_json, after_json, changes_json
            FROM (
                SELECT id, occurred_at, actor_user_id, action,
                       before_json::text AS before_json, after_json::text AS after_json,
                       changes_json::text AS changes_json
                FROM audit_log
                WHERE project_id = :projectId
                  AND entity_type = 'WORK_ITEM'
                  AND entity_id = :workItemId
                ORDER BY occurred_at DESC, id DESC
                LIMIT :limit
            ) recent
            ORDER BY occurred_at, id
            """)
    List<WorkItemTimelineRow> findWorkItemTimeline(@Param("projectId") String projectId,
                                                   @Param("workItemId") String workItemId,
                                                   @Param("limit") int limit);

    @Query("""
            SELECT id, occurred_at, actor_user_id, action, entity_type, entity_id, project_id,
                   outcome, summary, before_json::text AS before_json, after_json::text AS after_json,
                   changes_json::text AS changes_json, metadata_json::text AS metadata_json
            FROM audit_log
            ORDER BY occurred_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<AuditLog> findPage(@Param("limit") int limit,
                            @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM audit_log")
    long countLogs();

    @Query("""
            SELECT id, occurred_at, actor_user_id, action, entity_type, entity_id, project_id,
                   outcome, summary, before_json::text AS before_json, after_json::text AS after_json,
                   changes_json::text AS changes_json, metadata_json::text AS metadata_json
            FROM audit_log
            WHERE project_id = :projectId
            ORDER BY occurred_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<AuditLog> findPageByProjectId(@Param("projectId") String projectId,
                                       @Param("limit") int limit,
                                       @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM audit_log
            WHERE project_id = :projectId
            """)
    long countLogsByProjectId(@Param("projectId") String projectId);

    @Query("""
            SELECT id, occurred_at, actor_user_id, action, entity_type, entity_id, project_id,
                   outcome, summary, before_json::text AS before_json, after_json::text AS after_json,
                   changes_json::text AS changes_json, metadata_json::text AS metadata_json
            FROM audit_log
            WHERE project_id = :projectId
              AND (
                (entity_type = 'WORK_ITEM' AND entity_id = :workItemId)
                OR (entity_type = 'ENTRY' AND EXISTS (
                    SELECT 1 FROM entry e WHERE e.id = entity_id AND e.work_item_id = :workItemId))
                OR (entity_type = 'RELATIONSHIP' AND (
                    metadata_json ->> 'fromEntityId' = :workItemId
                    OR metadata_json ->> 'toEntityId' = :workItemId))
              )
            ORDER BY occurred_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<AuditLog> findPageByWorkItemId(@Param("workItemId") String workItemId,
                                        @Param("projectId") String projectId,
                                        @Param("limit") int limit,
                                        @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM audit_log
            WHERE project_id = :projectId
              AND (
                (entity_type = 'WORK_ITEM' AND entity_id = :workItemId)
                OR (entity_type = 'ENTRY' AND EXISTS (
                    SELECT 1 FROM entry e WHERE e.id = entity_id AND e.work_item_id = :workItemId))
                OR (entity_type = 'RELATIONSHIP' AND (
                    metadata_json ->> 'fromEntityId' = :workItemId
                    OR metadata_json ->> 'toEntityId' = :workItemId))
              )
            """)
    long countLogsByWorkItemId(@Param("workItemId") String workItemId,
                               @Param("projectId") String projectId);

    @Modifying
    @Query("""
            INSERT INTO audit_log (
                id,
                occurred_at,
                actor_user_id,
                action,
                entity_type,
                entity_id,
                project_id,
                outcome,
                summary,
                before_json,
                after_json,
                changes_json,
                metadata_json
            )
            VALUES (
                :id,
                :occurredAt,
                :actorUserId,
                :action,
                :entityType,
                :entityId,
                :projectId,
                :outcome,
                :summary,
                CAST(:beforeJson AS jsonb),
                CAST(:afterJson AS jsonb),
                CAST(:changesJson AS jsonb),
                CAST(:metadataJson AS jsonb)
            )
            """)
    void insert(@Param("id") String id,
                @Param("occurredAt") OffsetDateTime occurredAt,
                @Param("actorUserId") String actorUserId,
                @Param("action") String action,
                @Param("entityType") String entityType,
                @Param("entityId") String entityId,
                @Param("projectId") String projectId,
                @Param("outcome") String outcome,
                @Param("summary") String summary,
                @Param("beforeJson") String beforeJson,
                @Param("afterJson") String afterJson,
                @Param("changesJson") String changesJson,
                @Param("metadataJson") String metadataJson);
}
