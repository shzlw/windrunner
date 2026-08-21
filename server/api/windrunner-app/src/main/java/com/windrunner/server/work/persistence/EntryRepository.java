package com.windrunner.server.work.persistence;

import com.windrunner.server.work.domain.Entry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EntryRepository extends CrudRepository<Entry, String> {
    String COLUMNS = "id, project_id, work_item_id, sort_index, author_user_id, type, body, created_at, updated_at";
    @Query("SELECT " + COLUMNS + " FROM entry WHERE project_id = :projectId ORDER BY work_item_id, sort_index, id") List<Entry> findByProjectId(@Param("projectId") String projectId);
    @Query("SELECT " + COLUMNS + " FROM entry WHERE work_item_id = :workItemId ORDER BY sort_index, id") List<Entry> findByWorkItemId(@Param("workItemId") String workItemId);
    @Query("SELECT " + COLUMNS + " FROM entry WHERE id = :id") Optional<Entry> findById(@Param("id") String id);
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
    @Query("SELECT COALESCE(MAX(sort_index), 0) FROM entry WHERE project_id = :projectId AND work_item_id = :workItemId") int maxSortIndex(@Param("projectId") String projectId, @Param("workItemId") String workItemId);
    @Modifying @Query("INSERT INTO entry (id, project_id, work_item_id, sort_index, author_user_id, type, body, search_vec) VALUES (:id, :projectId, :workItemId, :sortIndex, :authorUserId, :type, :body, to_tsvector('simple', :searchVec))") void insert(@Param("id") String id, @Param("projectId") String projectId, @Param("workItemId") String workItemId, @Param("sortIndex") int sortIndex, @Param("authorUserId") String authorUserId, @Param("type") String type, @Param("body") String body, @Param("searchVec") String searchVec);
    @Modifying @Query("UPDATE entry SET type = :type, body = :body, updated_at = NOW(), search_vec = to_tsvector('simple', :searchVec) WHERE id = :id AND project_id = :projectId") int update(@Param("id") String id, @Param("projectId") String projectId, @Param("type") String type, @Param("body") String body, @Param("searchVec") String searchVec);
    @Modifying @Query("UPDATE entry SET sort_index = :sortIndex WHERE id = :id AND project_id = :projectId") int updateSortIndex(@Param("id") String id, @Param("projectId") String projectId, @Param("sortIndex") int sortIndex);
    @Modifying @Query("DELETE FROM entry WHERE id = :id AND project_id = :projectId") int deleteInProject(@Param("id") String id, @Param("projectId") String projectId);
    @Modifying @Query("DELETE FROM entry WHERE project_id = :projectId AND work_item_id = :workItemId") int deleteByWorkItemId(@Param("projectId") String projectId, @Param("workItemId") String workItemId);
}
