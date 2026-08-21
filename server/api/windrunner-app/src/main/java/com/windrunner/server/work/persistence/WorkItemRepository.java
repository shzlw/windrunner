package com.windrunner.server.work.persistence;

import com.windrunner.server.work.domain.WorkItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkItemRepository extends CrudRepository<WorkItem, String> {
    String COLUMNS = "id, project_id, parent_work_item_id, sort_index, type, title, status, due_date, priority, created_by_user_id, created_at, updated_at";
    @Query("SELECT " + COLUMNS + " FROM work_item WHERE project_id = :projectId ORDER BY parent_work_item_id NULLS FIRST, sort_index, id")
    List<WorkItem> findByProjectId(@Param("projectId") String projectId);
    @Query("""
            SELECT w.id, w.project_id, w.parent_work_item_id, w.sort_index, w.type, w.title, w.status, w.due_date, w.priority, w.created_by_user_id, w.created_at, w.updated_at
            FROM work_item w, websearch_to_tsquery('simple', :ftsQuery) q
            WHERE w.project_id = :projectId
              AND (w.search_vec @@ q OR w.title % :rawQuery)
            ORDER BY ts_rank_cd(w.search_vec, q) DESC, w.updated_at DESC, w.id
            LIMIT :limit
            """)
    List<WorkItem> searchInProject(@Param("projectId") String projectId,
                                   @Param("ftsQuery") String ftsQuery,
                                   @Param("rawQuery") String rawQuery,
                                   @Param("limit") int limit);
    @Query("SELECT " + COLUMNS + " FROM work_item WHERE id IN (:ids)")
    List<WorkItem> findByIds(@Param("ids") java.util.Collection<String> ids);
    @Query("SELECT " + COLUMNS + " FROM work_item WHERE id = :id") Optional<WorkItem> findById(@Param("id") String id);
    @Query("SELECT EXISTS(SELECT 1 FROM work_item WHERE id = :id AND project_id = :projectId)") boolean existsInProject(@Param("id") String id, @Param("projectId") String projectId);
    @Query("SELECT COALESCE(MAX(sort_index), 0) FROM work_item WHERE project_id = :projectId AND (:parentId IS NULL AND parent_work_item_id IS NULL OR parent_work_item_id = :parentId)") int maxSortIndex(@Param("projectId") String projectId, @Param("parentId") String parentId);
    @Query("SELECT " + COLUMNS + " FROM work_item WHERE project_id = :projectId AND (:parentId IS NULL AND parent_work_item_id IS NULL OR parent_work_item_id = :parentId) ORDER BY sort_index, id") List<WorkItem> findByParent(@Param("projectId") String projectId, @Param("parentId") String parentId);
    @Modifying @Query("INSERT INTO work_item (id, project_id, parent_work_item_id, sort_index, type, title, status, due_date, priority, created_by_user_id, search_vec) VALUES (:id, :projectId, :parentId, :sortIndex, :type, :title, :status, :dueDate, :priority, :createdByUserId, to_tsvector('simple', :searchVec))")
    void insert(@Param("id") String id, @Param("projectId") String projectId, @Param("parentId") String parentId, @Param("sortIndex") int sortIndex, @Param("type") String type, @Param("title") String title, @Param("status") String status, @Param("dueDate") java.time.LocalDate dueDate, @Param("priority") String priority, @Param("createdByUserId") String createdByUserId, @Param("searchVec") String searchVec);
    @Modifying @Query("UPDATE work_item SET parent_work_item_id = :parentId, sort_index = :sortIndex, type = :type, title = :title, status = :status, due_date = :dueDate, priority = :priority, updated_at = NOW(), search_vec = to_tsvector('simple', :searchVec) WHERE id = :id AND project_id = :projectId")
    int update(@Param("id") String id, @Param("projectId") String projectId, @Param("parentId") String parentId, @Param("sortIndex") int sortIndex, @Param("type") String type, @Param("title") String title, @Param("status") String status, @Param("dueDate") java.time.LocalDate dueDate, @Param("priority") String priority, @Param("searchVec") String searchVec);
    @Modifying @Query("UPDATE work_item SET sort_index = :sortIndex WHERE id = :id AND project_id = :projectId") int updateSortIndex(@Param("id") String id, @Param("projectId") String projectId, @Param("sortIndex") int sortIndex);
    @Modifying @Query("UPDATE work_item SET parent_work_item_id = :parentId, sort_index = :sortIndex, updated_at = NOW() WHERE id = :id AND project_id = :projectId") int updateParentAndSortIndex(@Param("id") String id, @Param("projectId") String projectId, @Param("parentId") String parentId, @Param("sortIndex") int sortIndex);
    @Modifying @Query("DELETE FROM work_item WHERE id = :id AND project_id = :projectId") int deleteInProject(@Param("id") String id, @Param("projectId") String projectId);

    record AssignedRow(
            String projectId,
            String projectName,
            String workItemId,
            String title,
            String type,
            String status,
            java.time.LocalDate dueDate,
            String priority,
            java.time.OffsetDateTime updatedAt
    ) {
    }

    @Query("""
            SELECT w.project_id, p.name AS project_name, w.id AS work_item_id, w.title, w.type, w.status,
                   w.due_date, w.priority, w.updated_at
            FROM work_item w
            JOIN project p ON p.id = w.project_id
            WHERE EXISTS (
                SELECT 1 FROM work_item_assignee a
                WHERE a.work_item_id = w.id
                  AND (
                    (a.assignee_type = 'USER' AND a.assignee_id = :userId)
                    OR (a.assignee_type = 'TEAM' AND EXISTS (
                        SELECT 1 FROM team_member tm
                        WHERE tm.team_id = a.assignee_id AND tm.user_id = :userId))
                  )
            )
            AND w.project_id IN (:projectIds)
            ORDER BY w.due_date NULLS LAST, w.updated_at DESC, w.id
            LIMIT :limit OFFSET :offset
            """)
    List<AssignedRow> listAssignedToUser(@Param("userId") String userId,
                                         @Param("projectIds") List<String> projectIds,
                                         @Param("limit") int limit,
                                         @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM work_item w
            WHERE EXISTS (
                SELECT 1 FROM work_item_assignee a
                WHERE a.work_item_id = w.id
                  AND (
                    (a.assignee_type = 'USER' AND a.assignee_id = :userId)
                    OR (a.assignee_type = 'TEAM' AND EXISTS (
                        SELECT 1 FROM team_member tm
                        WHERE tm.team_id = a.assignee_id AND tm.user_id = :userId))
                  )
            )
            AND w.project_id IN (:projectIds)
            """)
    long countAssignedToUser(@Param("userId") String userId, @Param("projectIds") List<String> projectIds);
}
