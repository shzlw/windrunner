package com.windrunner.server.work.persistence;

import com.windrunner.server.work.domain.WorkItemAssignee;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface WorkItemAssigneeRepository extends CrudRepository<WorkItemAssignee, String> {
    record AssigneeCount(String assigneeType, String assigneeId, String assigneeLabel, long count) {
    }

    @Query("""
            SELECT a.assignee_type AS assignee_type,
                   a.assignee_id AS assignee_id,
                   COALESCE(u.display_name, u.username, t.name, a.assignee_id) AS assignee_label,
                   COUNT(*) AS count
            FROM work_item_assignee a
            JOIN work_item w ON w.id = a.work_item_id
            LEFT JOIN app_user u ON a.assignee_type = 'USER' AND u.id = a.assignee_id
            LEFT JOIN team t ON a.assignee_type = 'TEAM' AND t.id = a.assignee_id
            WHERE w.project_id = :projectId
            GROUP BY a.assignee_type, a.assignee_id, u.display_name, u.username, t.name
            ORDER BY count DESC, a.assignee_type, a.assignee_id
            """)
    List<AssigneeCount> countByProjectId(@Param("projectId") String projectId);

    @Query("SELECT id, work_item_id, assignee_type, assignee_id, created_at FROM work_item_assignee WHERE work_item_id = :workItemId ORDER BY assignee_type, assignee_id")
    List<WorkItemAssignee> findByWorkItemId(@Param("workItemId") String workItemId);

    @Query("SELECT id, work_item_id, assignee_type, assignee_id, created_at FROM work_item_assignee WHERE work_item_id IN (:workItemIds) ORDER BY work_item_id, assignee_type, assignee_id")
    List<WorkItemAssignee> findByWorkItemIds(@Param("workItemIds") Collection<String> workItemIds);

    @Modifying
    @Query("INSERT INTO work_item_assignee (id, work_item_id, assignee_type, assignee_id) VALUES (:id, :workItemId, :type, :assigneeId)")
    void insert(@Param("id") String id, @Param("workItemId") String workItemId, @Param("type") String type, @Param("assigneeId") String assigneeId);

    @Modifying
    @Query("DELETE FROM work_item_assignee WHERE work_item_id = :workItemId")
    int deleteByWorkItemId(@Param("workItemId") String workItemId);

    @Modifying
    @Query("DELETE FROM work_item_assignee WHERE assignee_type = :assigneeType AND assignee_id = :assigneeId")
    int deleteByAssignee(@Param("assigneeType") String assigneeType, @Param("assigneeId") String assigneeId);
}
