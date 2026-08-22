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

    @Query("SELECT id, project_id, from_entity_type, from_entity_id, to_entity_type, to_entity_id, type, reason, source_entry_id, created_by_user_id, created_at FROM relationship WHERE id = :id")
    Optional<Relationship> findById(@Param("id") String id);

    @Query("""
            SELECT r.id, r.project_id, r.from_entity_type, r.from_entity_id, r.to_entity_type, r.to_entity_id, r.type, r.reason, r.source_entry_id, r.created_by_user_id, r.created_at
            FROM relationship r
            WHERE r.project_id = :projectId
              AND (:type IS NULL OR r.type = :type)
              AND (:createdAfter IS NULL OR r.created_at > :createdAfter)
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
              AND (:createdAfter IS NULL OR r.created_at > :createdAfter)
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
}
