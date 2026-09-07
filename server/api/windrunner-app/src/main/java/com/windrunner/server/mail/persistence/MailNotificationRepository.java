package com.windrunner.server.mail.persistence;

import com.windrunner.server.mail.domain.MailNotification;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MailNotificationRepository extends CrudRepository<MailNotification, String> {
    @Modifying
    @Query("""
            INSERT INTO mail_notification
                (id, recipient_user_id, recipient_email, work_item_id, actor_user_id, body, available_at)
            VALUES (:id, :userId, :email, :workItemId, :actorId, :body,
                    NOW() + :delaySeconds * INTERVAL '1 second')
            """)
    void insert(@Param("id") String id, @Param("userId") String userId,
                @Param("email") String email, @Param("workItemId") String workItemId,
                @Param("actorId") String actorId, @Param("body") String body,
                @Param("delaySeconds") int delaySeconds);

    @Query("""
            SELECT DISTINCT recipient_user_id FROM mail_notification
            WHERE available_at <= NOW() AND attempts < 3
            LIMIT 20
            """)
    List<String> findReadyRecipients();

    // Lock the recipient as well as rows to prevent two workers splitting one digest.
    @Query("SELECT id FROM app_user WHERE id = :userId FOR UPDATE SKIP LOCKED")
    List<String> lockRecipient(@Param("userId") String userId);

    @Query("""
            SELECT * FROM mail_notification
            WHERE recipient_user_id = :userId AND attempts < 3
              AND (available_at <= NOW() OR (work_item_id IS NOT NULL AND attempts = 0 AND EXISTS (
                    SELECT 1 FROM mail_notification due
                    WHERE due.recipient_user_id = :userId AND due.work_item_id IS NOT NULL
                      AND due.available_at <= NOW() AND due.attempts < 3)))
            ORDER BY available_at, id LIMIT 100
            FOR UPDATE SKIP LOCKED
            """)
    List<MailNotification> findReadyForUpdate(@Param("userId") String userId);

    @Modifying
    @Query("""
            UPDATE mail_notification SET attempts = attempts + 1,
                available_at = NOW() + INTERVAL '5 minutes'
            WHERE id = :id
            """)
    void retry(@Param("id") String id);

    @Modifying
    @Query("DELETE FROM mail_notification WHERE created_at < NOW() - INTERVAL '30 days'")
    void deleteExpired();
}
