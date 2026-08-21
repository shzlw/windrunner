package com.windrunner.server.notification.persistence;

import com.windrunner.server.notification.domain.UserNotification;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserNotificationRepository extends CrudRepository<UserNotification, String> {

    String COLUMNS = "id, recipient_user_id, notification_type, actor_user_id, project_id, work_item_id, title, message, read_at, created_at";

    @Modifying
    @Query("""
            INSERT INTO user_notification (
                id, recipient_user_id, notification_type, actor_user_id,
                project_id, work_item_id, title, message
            ) VALUES (
                :id, :recipientUserId, :notificationType, :actorUserId,
                :projectId, :workItemId, :title, :message
            )
            """)
    void insert(@Param("id") String id,
                @Param("recipientUserId") String recipientUserId,
                @Param("notificationType") String notificationType,
                @Param("actorUserId") String actorUserId,
                @Param("projectId") String projectId,
                @Param("workItemId") String workItemId,
                @Param("title") String title,
                @Param("message") String message);

    @Query("SELECT " + COLUMNS + " FROM user_notification WHERE recipient_user_id = :userId ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
    List<UserNotification> findForUser(@Param("userId") String userId,
                                       @Param("limit") int limit,
                                       @Param("offset") long offset);

    @Query("SELECT " + COLUMNS + " FROM user_notification WHERE recipient_user_id = :userId AND read_at IS NULL ORDER BY created_at DESC, id DESC LIMIT :limit")
    List<UserNotification> findUnreadForUser(@Param("userId") String userId,
                                             @Param("limit") int limit);

    @Query("SELECT " + COLUMNS + " FROM user_notification WHERE recipient_user_id = :userId AND created_at > :createdAfter ORDER BY created_at ASC, id ASC LIMIT :limit")
    List<UserNotification> findCreatedAfter(@Param("userId") String userId,
                                            @Param("createdAfter") OffsetDateTime createdAfter,
                                            @Param("limit") int limit);

    @Query("SELECT COUNT(*) FROM user_notification WHERE recipient_user_id = :userId")
    long countForUser(@Param("userId") String userId);

    @Query("SELECT COUNT(*) FROM user_notification WHERE recipient_user_id = :userId AND read_at IS NULL")
    long countUnreadForUser(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE user_notification SET read_at = NOW() WHERE id = :id AND recipient_user_id = :userId AND read_at IS NULL")
    int markRead(@Param("id") String id, @Param("userId") String userId);

    @Modifying
    @Query("UPDATE user_notification SET read_at = NOW() WHERE recipient_user_id = :userId AND read_at IS NULL")
    int markAllRead(@Param("userId") String userId);
}
