package com.windrunner.server.work.persistence;

import com.windrunner.server.work.domain.Entry;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntryRepository extends CrudRepository<Entry, String> {
    String COLUMNS = "id, project_id, work_item_id, sort_index, author_user_id, type, body, created_at, updated_at";

    @Query("SELECT " + COLUMNS + " FROM entry WHERE project_id = :projectId ORDER BY work_item_id, sort_index, id")
    List<Entry> findByProjectId(@Param("projectId") String projectId);

    @Query("SELECT " + COLUMNS + " FROM entry WHERE project_id = :projectId ORDER BY created_at DESC, id LIMIT :limit OFFSET :offset")
    List<Entry> findPageByProjectId(@Param("projectId") String projectId,
                                    @Param("limit") int limit,
                                    @Param("offset") long offset);

    @Query("SELECT " + COLUMNS + " FROM entry WHERE project_id = :projectId AND work_item_id = :workItemId ORDER BY sort_index, id LIMIT :limit OFFSET :offset")
    List<Entry> findPageByProjectIdAndWorkItemId(@Param("projectId") String projectId,
                                                 @Param("workItemId") String workItemId,
                                                 @Param("limit") int limit,
                                                 @Param("offset") long offset);

    record DistributionRow(String value, long count) {
    }

    @Query("SELECT COUNT(*) FROM entry WHERE project_id = :projectId")
    long countAllByProjectId(@Param("projectId") String projectId);

    @Query("SELECT COUNT(*) FROM entry WHERE project_id = :projectId AND work_item_id = :workItemId")
    long countByProjectIdAndWorkItemId(@Param("projectId") String projectId,
                                       @Param("workItemId") String workItemId);

    @Query("SELECT COALESCE(type, 'UNSET') AS value, COUNT(*) AS count FROM entry WHERE project_id = :projectId GROUP BY type ORDER BY value")
    List<DistributionRow> countByTypeForProject(@Param("projectId") String projectId);

    @Query("SELECT " + COLUMNS + " FROM entry WHERE work_item_id = :workItemId ORDER BY sort_index, id")
    List<Entry> findByWorkItemId(@Param("workItemId") String workItemId);

    @Query("SELECT " + COLUMNS + " FROM entry WHERE id = :id")
    Optional<Entry> findById(@Param("id") String id);

    @Query("""
            SELECT e.id, e.project_id, e.work_item_id, e.sort_index, e.author_user_id, e.type, e.body, e.created_at, e.updated_at
            FROM entry e
            WHERE e.work_item_id = :workItemId
              AND (CAST(:updatedAfter AS TIMESTAMPTZ) IS NULL OR e.updated_at > CAST(:updatedAfter AS TIMESTAMPTZ))
            ORDER BY e.updated_at DESC, e.id
            LIMIT :limit OFFSET :offset
            """)
    List<Entry> findPageByWorkItemId(@Param("workItemId") String workItemId,
                                     @Param("updatedAfter") java.time.OffsetDateTime updatedAfter,
                                     @Param("limit") int limit,
                                     @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM entry e
            WHERE e.work_item_id = :workItemId
              AND (CAST(:updatedAfter AS TIMESTAMPTZ) IS NULL OR e.updated_at > CAST(:updatedAfter AS TIMESTAMPTZ))
            """)
    long countByWorkItemId(@Param("workItemId") String workItemId,
                           @Param("updatedAfter") java.time.OffsetDateTime updatedAfter);

    @Query("""
            SELECT e.id, e.project_id, e.work_item_id, e.sort_index, e.author_user_id, e.type, e.body, e.created_at, e.updated_at
            FROM entry e
            WHERE e.project_id = :projectId
              AND e.work_item_id = :workItemId
              AND (CAST(:updatedAfter AS TIMESTAMPTZ) IS NULL OR e.updated_at > CAST(:updatedAfter AS TIMESTAMPTZ))
            ORDER BY e.updated_at DESC, e.id
            LIMIT :limit OFFSET :offset
            """)
    List<Entry> findExternalPageByProjectIdAndWorkItemId(@Param("projectId") String projectId,
                                                         @Param("workItemId") String workItemId,
                                                         @Param("updatedAfter") java.time.OffsetDateTime updatedAfter,
                                                         @Param("limit") int limit,
                                                         @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM entry e
            WHERE e.project_id = :projectId
              AND e.work_item_id = :workItemId
              AND (CAST(:updatedAfter AS TIMESTAMPTZ) IS NULL OR e.updated_at > CAST(:updatedAfter AS TIMESTAMPTZ))
            """)
    long countExternalByProjectIdAndWorkItemId(@Param("projectId") String projectId,
                                               @Param("workItemId") String workItemId,
                                               @Param("updatedAfter") java.time.OffsetDateTime updatedAfter);

    @Query("""
            SELECT e.id, e.project_id, e.work_item_id, e.sort_index, e.author_user_id, e.type, e.body, e.created_at, e.updated_at
            FROM entry e, websearch_to_tsquery('simple', :ftsQuery) q
            WHERE e.project_id = :projectId
              AND (e.search_vec @@ q OR e.body % :rawQuery)
            ORDER BY ts_rank_cd(e.search_vec, q) DESC, e.created_at DESC, e.id
            LIMIT :limit
            """)
    List<Entry> searchInProject(@Param("projectId") String projectId,
                                @Param("ftsQuery") String ftsQuery,
                                @Param("rawQuery") String rawQuery,
                                @Param("limit") int limit);

    @Query("SELECT COALESCE(MAX(sort_index), 0) FROM entry WHERE project_id = :projectId AND work_item_id = :workItemId")
    int maxSortIndex(@Param("projectId") String projectId, @Param("workItemId") String workItemId);

    @Modifying
    @Query("INSERT INTO entry (id, project_id, work_item_id, sort_index, author_user_id, type, body, search_vec) VALUES (:id, :projectId, :workItemId, :sortIndex, :authorUserId, :type, :body, to_tsvector('simple', :searchVec))")
    void insert(@Param("id") String id, @Param("projectId") String projectId, @Param("workItemId") String workItemId, @Param("sortIndex") int sortIndex, @Param("authorUserId") String authorUserId, @Param("type") String type, @Param("body") String body, @Param("searchVec") String searchVec);

    @Modifying
    @Query("UPDATE entry SET type = :type, body = :body, updated_at = NOW(), search_vec = to_tsvector('simple', :searchVec) WHERE id = :id AND project_id = :projectId")
    int update(@Param("id") String id, @Param("projectId") String projectId, @Param("type") String type, @Param("body") String body, @Param("searchVec") String searchVec);

    @Modifying
    @Query("UPDATE entry SET sort_index = :sortIndex WHERE id = :id AND project_id = :projectId")
    int updateSortIndex(@Param("id") String id, @Param("projectId") String projectId, @Param("sortIndex") int sortIndex);

    @Modifying
    @Query("DELETE FROM entry WHERE id = :id AND project_id = :projectId")
    int deleteInProject(@Param("id") String id, @Param("projectId") String projectId);

    @Modifying
    @Query("DELETE FROM entry WHERE project_id = :projectId AND work_item_id = :workItemId")
    int deleteByWorkItemId(@Param("projectId") String projectId, @Param("workItemId") String workItemId);

    @Modifying
    @Query("DELETE FROM entry WHERE project_id = :projectId")
    int deleteByProjectId(@Param("projectId") String projectId);
}
