package com.windrunner.server.work.persistence;

import com.windrunner.server.work.domain.WorkItemAssignee;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkItemAssigneeRepository extends CrudRepository<WorkItemAssignee, String> {
    @Query("SELECT id, work_item_id, assignee_type, assignee_id, created_at FROM work_item_assignee WHERE work_item_id = :workItemId ORDER BY assignee_type, assignee_id") List<WorkItemAssignee> findByWorkItemId(@Param("workItemId") String workItemId);
    @Modifying @Query("INSERT INTO work_item_assignee (id, work_item_id, assignee_type, assignee_id) VALUES (:id, :workItemId, :type, :assigneeId)") void insert(@Param("id") String id, @Param("workItemId") String workItemId, @Param("type") String type, @Param("assigneeId") String assigneeId);
    @Modifying @Query("DELETE FROM work_item_assignee WHERE work_item_id = :workItemId") int deleteByWorkItemId(@Param("workItemId") String workItemId);
    @Modifying @Query("DELETE FROM work_item_assignee WHERE assignee_type = :assigneeType AND assignee_id = :assigneeId") int deleteByAssignee(@Param("assigneeType") String assigneeType, @Param("assigneeId") String assigneeId);
}
