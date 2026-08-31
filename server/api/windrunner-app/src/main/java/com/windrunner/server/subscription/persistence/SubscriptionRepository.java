package com.windrunner.server.subscription.persistence;

import com.windrunner.server.subscription.domain.WorkItemSubscription;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface SubscriptionRepository extends CrudRepository<WorkItemSubscription, String> {

    record SubscriptionRow(
            String userId,
            String projectId,
            String projectName,
            String workItemId,
            String workItemTitle,
            String workItemType,
            String parentWorkItemId,
            String parentWorkItemTitle,
            OffsetDateTime subscribedAt
    ) {
    }

    @Modifying
    @Query("""
            INSERT INTO work_item_subscription (id, user_id, project_id, work_item_id)
            VALUES (:id, :userId, :projectId, :workItemId)
            ON CONFLICT (user_id, work_item_id) DO NOTHING
            """)
    void insert(@Param("id") String id,
                @Param("userId") String userId,
                @Param("projectId") String projectId,
                @Param("workItemId") String workItemId);

    @Modifying
    @Query("DELETE FROM work_item_subscription WHERE user_id = :userId AND work_item_id = :workItemId")
    int delete(@Param("userId") String userId, @Param("workItemId") String workItemId);

    @Modifying
    @Query("DELETE FROM work_item_subscription WHERE project_id = :projectId")
    int deleteByProjectId(@Param("projectId") String projectId);

    @Query("SELECT EXISTS(SELECT 1 FROM work_item_subscription WHERE user_id = :userId AND work_item_id = :workItemId)")
    boolean exists(@Param("userId") String userId, @Param("workItemId") String workItemId);

    @Query("SELECT user_id FROM work_item_subscription WHERE work_item_id = :workItemId")
    List<String> findUserIdsByWorkItemId(@Param("workItemId") String workItemId);

    @Query("""
            SELECT s.user_id,
                   s.project_id,
                   p.name AS project_name,
                   s.work_item_id,
                   w.title AS work_item_title,
                   w.type AS work_item_type,
                   w.parent_work_item_id,
                   wp.title AS parent_work_item_title,
                   s.created_at AS subscribed_at
            FROM work_item_subscription s
            JOIN project p ON p.id = s.project_id
            JOIN work_item w ON w.id = s.work_item_id
            LEFT JOIN work_item wp ON wp.id = w.parent_work_item_id
            WHERE s.user_id = :userId
              AND s.project_id IN (:projectIds)
            ORDER BY s.created_at DESC, s.id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<SubscriptionRow> listForUser(@Param("userId") String userId,
                                      @Param("projectIds") List<String> projectIds,
                                      @Param("limit") int limit,
                                      @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM work_item_subscription s
            WHERE s.user_id = :userId
              AND s.project_id IN (:projectIds)
            """)
    long countForUser(@Param("userId") String userId, @Param("projectIds") List<String> projectIds);
}
