package com.windrunner.server.work.persistence;

import com.windrunner.server.work.domain.Relationship;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelationshipRepository extends CrudRepository<Relationship, String> {
    @Query("SELECT id, project_id, from_entity_type, from_entity_id, to_entity_type, to_entity_id, type, reason, source_entry_id, created_by_user_id, created_at FROM relationship WHERE project_id = :projectId ORDER BY created_at, id")
    List<Relationship> findByProjectId(@Param("projectId") String projectId);

    record DistributionRow(String value, long count) {
    }

    record BlockerRow(
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

    @Query("SELECT COUNT(*) FROM relationship WHERE project_id = :projectId")
    long countAllByProjectId(@Param("projectId") String projectId);

    @Query("SELECT COALESCE(type, 'UNSET') AS value, COUNT(*) AS count FROM relationship WHERE project_id = :projectId GROUP BY type ORDER BY value")
    List<DistributionRow> countByTypeForProject(@Param("projectId") String projectId);

    @Query("""
            SELECT COUNT(*)
            FROM relationship r
            JOIN work_item blocked
              ON blocked.id = r.from_entity_id
             AND blocked.project_id = r.project_id
            JOIN work_item blocker
              ON blocker.id = r.to_entity_id
             AND blocker.project_id = r.project_id
            WHERE r.project_id = :projectId
              AND r.type = 'BLOCKED_BY'
              AND r.from_entity_type = 'WORK_ITEM'
              AND r.to_entity_type = 'WORK_ITEM'
            """)
    long countWorkItemBlockers(@Param("projectId") String projectId);

    @Query("""
            SELECT COUNT(DISTINCT r.from_entity_id)
            FROM relationship r
            JOIN work_item blocked
              ON blocked.id = r.from_entity_id
             AND blocked.project_id = r.project_id
            JOIN work_item blocker
              ON blocker.id = r.to_entity_id
             AND blocker.project_id = r.project_id
            WHERE r.project_id = :projectId
              AND r.type = 'BLOCKED_BY'
              AND r.from_entity_type = 'WORK_ITEM'
              AND r.to_entity_type = 'WORK_ITEM'
            """)
    long countBlockedWorkItems(@Param("projectId") String projectId);

    @Query("""
            SELECT id, project_id, from_entity_type, from_entity_id, to_entity_type, to_entity_id, type, reason, source_entry_id, created_by_user_id, created_at
            FROM relationship
            WHERE project_id = :projectId
              AND (from_entity_id = :entityId OR to_entity_id = :entityId)
            ORDER BY created_at DESC, id
            LIMIT :limit OFFSET :offset
            """)
    List<Relationship> findPageByProjectAndEntity(@Param("projectId") String projectId,
                                                  @Param("entityId") String entityId,
                                                  @Param("limit") int limit,
                                                  @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM relationship WHERE project_id = :projectId AND (from_entity_id = :entityId OR to_entity_id = :entityId)")
    long countByProjectAndEntity(@Param("projectId") String projectId,
                                 @Param("entityId") String entityId);

    @Query("""
            SELECT r.id AS relationship_id,
                   r.from_entity_id AS blocked_work_item_id,
                   blocked.title AS blocked_work_item_title,
                   blocked.status AS blocked_work_item_status,
                   r.to_entity_id AS blocker_work_item_id,
                   blocker.title AS blocker_work_item_title,
                   blocker.status AS blocker_work_item_status,
                   r.reason AS reason
            FROM relationship r
            JOIN work_item blocked
              ON blocked.id = r.from_entity_id
             AND blocked.project_id = r.project_id
            JOIN work_item blocker
              ON blocker.id = r.to_entity_id
             AND blocker.project_id = r.project_id
            WHERE r.project_id = :projectId
              AND r.type = 'BLOCKED_BY'
              AND r.from_entity_type = 'WORK_ITEM'
              AND r.to_entity_type = 'WORK_ITEM'
            ORDER BY blocked.title, blocked.id, blocker.title, blocker.id, r.id
            LIMIT :limit OFFSET :offset
            """)
    List<BlockerRow> findPageWorkItemBlockers(@Param("projectId") String projectId,
                                              @Param("limit") int limit,
                                              @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM relationship r
            JOIN work_item blocked
              ON blocked.id = r.from_entity_id
             AND blocked.project_id = r.project_id
            JOIN work_item blocker
              ON blocker.id = r.to_entity_id
             AND blocker.project_id = r.project_id
            WHERE r.project_id = :projectId
              AND r.type = 'BLOCKED_BY'
              AND r.from_entity_type = 'WORK_ITEM'
              AND r.to_entity_type = 'WORK_ITEM'
            """)
    long countAllWorkItemBlockers(@Param("projectId") String projectId);

    @Query("SELECT id, project_id, from_entity_type, from_entity_id, to_entity_type, to_entity_id, type, reason, source_entry_id, created_by_user_id, created_at FROM relationship WHERE id = :id")
    Optional<Relationship> findById(@Param("id") String id);

    @Query("""
            SELECT r.id, r.project_id, r.from_entity_type, r.from_entity_id, r.to_entity_type, r.to_entity_id, r.type, r.reason, r.source_entry_id, r.created_by_user_id, r.created_at
            FROM relationship r
            WHERE r.project_id = :projectId
              AND r.from_entity_type = :fromEntityType
              AND r.from_entity_id = :fromEntityId
              AND r.to_entity_type = :toEntityType
              AND r.to_entity_id = :toEntityId
              AND r.type = :relationshipType
            ORDER BY r.created_at DESC, r.id
            LIMIT :limit OFFSET :offset
            """)
    List<Relationship> findExactPage(@Param("projectId") String projectId,
                                     @Param("fromEntityType") String fromEntityType,
                                     @Param("fromEntityId") String fromEntityId,
                                     @Param("toEntityType") String toEntityType,
                                     @Param("toEntityId") String toEntityId,
                                     @Param("relationshipType") String relationshipType,
                                     @Param("limit") int limit,
                                     @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM relationship r
            WHERE r.project_id = :projectId
              AND r.from_entity_type = :fromEntityType
              AND r.from_entity_id = :fromEntityId
              AND r.to_entity_type = :toEntityType
              AND r.to_entity_id = :toEntityId
              AND r.type = :relationshipType
            """)
    long countExact(@Param("projectId") String projectId,
                    @Param("fromEntityType") String fromEntityType,
                    @Param("fromEntityId") String fromEntityId,
                    @Param("toEntityType") String toEntityType,
                    @Param("toEntityId") String toEntityId,
                    @Param("relationshipType") String relationshipType);

    @Query("""
            SELECT id, project_id, from_entity_type, from_entity_id, to_entity_type, to_entity_id, type, reason, source_entry_id, created_by_user_id, created_at
            FROM relationship
            WHERE project_id = :projectId
              AND ((from_entity_type = :entityType AND from_entity_id = :entityId)
                OR (to_entity_type = :entityType AND to_entity_id = :entityId))
            ORDER BY created_at DESC, id
            LIMIT :limit
            """)
    List<Relationship> findByEntity(@Param("projectId") String projectId,
                                    @Param("entityType") String entityType,
                                    @Param("entityId") String entityId,
                                    @Param("limit") int limit);

    @Query("""
            SELECT r.id, r.project_id, r.from_entity_type, r.from_entity_id, r.to_entity_type, r.to_entity_id, r.type, r.reason, r.source_entry_id, r.created_by_user_id, r.created_at
            FROM relationship r
            WHERE r.project_id = :projectId
              AND (:type IS NULL OR r.type = :type)
              AND (CAST(:createdAfter AS TIMESTAMPTZ) IS NULL OR r.created_at > CAST(:createdAfter AS TIMESTAMPTZ))
            ORDER BY r.created_at DESC, r.id
            LIMIT :limit OFFSET :offset
            """)
    List<Relationship> findPageByProjectId(@Param("projectId") String projectId,
                                           @Param("type") String type,
                                           @Param("createdAfter") java.time.OffsetDateTime createdAfter,
                                           @Param("limit") int limit,
                                           @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM relationship r
            WHERE r.project_id = :projectId
              AND (:type IS NULL OR r.type = :type)
              AND (CAST(:createdAfter AS TIMESTAMPTZ) IS NULL OR r.created_at > CAST(:createdAfter AS TIMESTAMPTZ))
            """)
    long countByProjectId(@Param("projectId") String projectId,
                          @Param("type") String type,
                          @Param("createdAfter") java.time.OffsetDateTime createdAfter);

    @Query("""
            SELECT r.id, r.project_id, r.from_entity_type, r.from_entity_id, r.to_entity_type, r.to_entity_id, r.type, r.reason, r.source_entry_id, r.created_by_user_id, r.created_at
            FROM relationship r, websearch_to_tsquery('simple', :ftsQuery) q
            WHERE r.project_id = :projectId
              AND (r.search_vec @@ q OR r.reason % :rawQuery)
            ORDER BY ts_rank_cd(r.search_vec, q) DESC, r.created_at DESC, r.id
            LIMIT :limit
            """)
    List<Relationship> searchInProject(@Param("projectId") String projectId,
                                       @Param("ftsQuery") String ftsQuery,
                                       @Param("rawQuery") String rawQuery,
                                       @Param("limit") int limit);

    @Modifying
    @Query("INSERT INTO relationship (id, project_id, from_entity_type, from_entity_id, to_entity_type, to_entity_id, type, reason, source_entry_id, created_by_user_id, search_vec) VALUES (:id, :projectId, :fromType, :fromId, :toType, :toId, :type, :reason, :sourceEntryId, :createdBy, to_tsvector('simple', :searchVec))")
    void insert(@Param("id") String id, @Param("projectId") String projectId, @Param("fromType") String fromType, @Param("fromId") String fromId, @Param("toType") String toType, @Param("toId") String toId, @Param("type") String type, @Param("reason") String reason, @Param("sourceEntryId") String sourceEntryId, @Param("createdBy") String createdBy, @Param("searchVec") String searchVec);

    @Modifying
    @Query("DELETE FROM relationship WHERE id = :id AND project_id = :projectId")
    int deleteInProject(@Param("id") String id, @Param("projectId") String projectId);

    @Modifying
    @Query("UPDATE relationship SET reason = :reason, search_vec = to_tsvector('simple', :searchVec) WHERE id = :id AND project_id = :projectId")
    int updateReason(@Param("id") String id, @Param("projectId") String projectId, @Param("reason") String reason, @Param("searchVec") String searchVec);

    @Modifying
    @Query("DELETE FROM relationship WHERE project_id = :projectId AND from_entity_type = 'WORK_ITEM' AND from_entity_id = :workItemId AND type = :type")
    int deleteFromWorkItemByType(@Param("projectId") String projectId, @Param("workItemId") String workItemId, @Param("type") String type);

    @Modifying
    @Query("DELETE FROM relationship WHERE project_id = :projectId AND ((from_entity_type = :entityType AND from_entity_id = :entityId) OR (to_entity_type = :entityType AND to_entity_id = :entityId) OR source_entry_id = :entityId)")
    int deleteForEntity(@Param("projectId") String projectId, @Param("entityType") String entityType, @Param("entityId") String entityId);

    @Modifying
    @Query("DELETE FROM relationship WHERE project_id = :projectId")
    int deleteByProjectId(@Param("projectId") String projectId);
}
