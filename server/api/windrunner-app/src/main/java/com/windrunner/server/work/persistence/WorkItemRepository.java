package com.windrunner.server.work.persistence;

import com.windrunner.server.work.domain.WorkItem;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    @Query("""
            SELECT w.id, w.project_id, w.parent_work_item_id, w.sort_index, w.type, w.title, w.status, w.due_date, w.priority, w.created_by_user_id, w.created_at, w.updated_at
            FROM work_item w
            WHERE w.project_id = :projectId
              AND (:status IS NULL OR w.status = :status)
              AND (:type IS NULL OR w.type = :type)
              AND (:priority IS NULL OR w.priority = :priority)
              AND (CAST(:updatedAfter AS TIMESTAMPTZ) IS NULL OR w.updated_at > CAST(:updatedAfter AS TIMESTAMPTZ))
            ORDER BY w.updated_at DESC, w.id
            LIMIT :limit OFFSET :offset
            """)
    List<WorkItem> findPageForProject(@Param("projectId") String projectId,
                                      @Param("status") String status,
                                      @Param("type") String type,
                                      @Param("priority") String priority,
                                      @Param("updatedAfter") java.time.OffsetDateTime updatedAfter,
                                      @Param("limit") int limit,
                                      @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM work_item w
            WHERE w.project_id = :projectId
              AND (:status IS NULL OR w.status = :status)
              AND (:type IS NULL OR w.type = :type)
              AND (:priority IS NULL OR w.priority = :priority)
              AND (CAST(:updatedAfter AS TIMESTAMPTZ) IS NULL OR w.updated_at > CAST(:updatedAfter AS TIMESTAMPTZ))
            """)
    long countForProject(@Param("projectId") String projectId,
                         @Param("status") String status,
                         @Param("type") String type,
                         @Param("priority") String priority,
                         @Param("updatedAfter") java.time.OffsetDateTime updatedAfter);

    @Query("SELECT " + COLUMNS + " FROM work_item WHERE id = :id")
    Optional<WorkItem> findById(@Param("id") String id);

    @Query("SELECT " + COLUMNS + " FROM work_item WHERE id = :id FOR UPDATE")
    Optional<WorkItem> findByIdForUpdate(@Param("id") String id);

    @Query("SELECT EXISTS(SELECT 1 FROM work_item WHERE id = :id AND project_id = :projectId)")
    boolean existsInProject(@Param("id") String id, @Param("projectId") String projectId);

    @Query("SELECT COALESCE(MAX(sort_index), 0) FROM work_item WHERE project_id = :projectId AND (:parentId IS NULL AND parent_work_item_id IS NULL OR parent_work_item_id = :parentId)")
    int maxSortIndex(@Param("projectId") String projectId, @Param("parentId") String parentId);

    @Query("SELECT " + COLUMNS + " FROM work_item WHERE project_id = :projectId AND (:parentId IS NULL AND parent_work_item_id IS NULL OR parent_work_item_id = :parentId) ORDER BY sort_index, id")
    List<WorkItem> findByParent(@Param("projectId") String projectId, @Param("parentId") String parentId);

    @Query("SELECT " + COLUMNS + " FROM work_item WHERE project_id = :projectId AND (:parentId IS NULL AND parent_work_item_id IS NULL OR parent_work_item_id = :parentId) ORDER BY sort_index, id LIMIT :limit OFFSET :offset")
    List<WorkItem> findPageByParent(@Param("projectId") String projectId,
                                    @Param("parentId") String parentId,
                                    @Param("limit") int limit,
                                    @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM work_item WHERE project_id = :projectId AND (:parentId IS NULL AND parent_work_item_id IS NULL OR parent_work_item_id = :parentId)")
    long countByParent(@Param("projectId") String projectId, @Param("parentId") String parentId);

    @Query("""
            WITH RECURSIVE subtree AS (
                SELECT w.id, w.project_id, w.parent_work_item_id, w.sort_index, w.type, w.title, w.status, w.due_date, w.priority, w.created_by_user_id, w.created_at, w.updated_at,
                       1 AS depth,
                       ARRAY[COALESCE(w.sort_index, 0)::bigint] AS sort_path
                FROM work_item w
                WHERE w.project_id = :projectId
                  AND w.parent_work_item_id = :rootWorkItemId
                UNION ALL
                SELECT child.id, child.project_id, child.parent_work_item_id, child.sort_index, child.type, child.title, child.status, child.due_date, child.priority, child.created_by_user_id, child.created_at, child.updated_at,
                       subtree.depth + 1,
                       subtree.sort_path || COALESCE(child.sort_index, 0)::bigint
                FROM work_item child
                JOIN subtree ON child.parent_work_item_id = subtree.id
                WHERE child.project_id = :projectId
                  AND subtree.depth < :maxDepth
            )
            SELECT id, project_id, parent_work_item_id, sort_index, type, title, status, due_date, priority, created_by_user_id, created_at, updated_at
            FROM subtree
            ORDER BY sort_path, id
            LIMIT :limit
            """)
    List<WorkItem> findSubtree(@Param("projectId") String projectId,
                               @Param("rootWorkItemId") String rootWorkItemId,
                               @Param("maxDepth") int maxDepth,
                               @Param("limit") int limit);

    @Modifying
    @Query("INSERT INTO work_item (id, project_id, parent_work_item_id, sort_index, type, title, status, due_date, priority, created_by_user_id, search_vec) VALUES (:id, :projectId, :parentId, :sortIndex, :type, :title, :status, :dueDate, :priority, :createdByUserId, to_tsvector('simple', :searchVec))")
    void insert(@Param("id") String id, @Param("projectId") String projectId, @Param("parentId") String parentId, @Param("sortIndex") int sortIndex, @Param("type") String type, @Param("title") String title, @Param("status") String status, @Param("dueDate") java.time.LocalDate dueDate, @Param("priority") String priority, @Param("createdByUserId") String createdByUserId, @Param("searchVec") String searchVec);

    @Modifying
    @Query("UPDATE work_item SET parent_work_item_id = :parentId, sort_index = :sortIndex, type = :type, title = :title, status = :status, due_date = :dueDate, priority = :priority, updated_at = NOW(), search_vec = to_tsvector('simple', :searchVec) WHERE id = :id AND project_id = :projectId")
    int update(@Param("id") String id, @Param("projectId") String projectId, @Param("parentId") String parentId, @Param("sortIndex") int sortIndex, @Param("type") String type, @Param("title") String title, @Param("status") String status, @Param("dueDate") java.time.LocalDate dueDate, @Param("priority") String priority, @Param("searchVec") String searchVec);

    @Modifying
    @Query("UPDATE work_item SET sort_index = :sortIndex WHERE id = :id AND project_id = :projectId")
    int updateSortIndex(@Param("id") String id, @Param("projectId") String projectId, @Param("sortIndex") int sortIndex);

    @Modifying
    @Query("UPDATE work_item SET parent_work_item_id = :parentId, sort_index = :sortIndex, updated_at = NOW() WHERE id = :id AND project_id = :projectId")
    int updateParentAndSortIndex(@Param("id") String id, @Param("projectId") String projectId, @Param("parentId") String parentId, @Param("sortIndex") int sortIndex);

    @Modifying
    @Query("DELETE FROM work_item WHERE id = :id AND project_id = :projectId")
    int deleteInProject(@Param("id") String id, @Param("projectId") String projectId);

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
