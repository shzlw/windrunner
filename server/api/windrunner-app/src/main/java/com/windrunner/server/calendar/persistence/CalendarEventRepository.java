package com.windrunner.server.calendar.persistence;

import com.windrunner.server.calendar.domain.CalendarEvent;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CalendarEventRepository extends CrudRepository<CalendarEvent, String> {

    String COLUMNS = "id, user_id, title, description, starts_at, ends_at, timezone, all_day, work_item_id, created_by_user_id, created_at, updated_at";

    @Query("SELECT " + COLUMNS + " FROM calendar_event WHERE id = :id")
    Optional<CalendarEvent> findById(@Param("id") String id);

    @Query("""
            SELECT id, user_id, title, description, starts_at, ends_at, timezone,
                   all_day, work_item_id, created_by_user_id, created_at, updated_at
            FROM calendar_event
            WHERE user_id = :userId
              AND ends_at > :from
              AND starts_at < :to
            ORDER BY starts_at, ends_at, id
            """)
    List<CalendarEvent> findByUserIdAndRange(@Param("userId") String userId,
                                             @Param("from") OffsetDateTime from,
                                             @Param("to") OffsetDateTime to);

    @Query("""
            SELECT id, user_id, title, description, starts_at, ends_at, timezone,
                   all_day, work_item_id, created_by_user_id, created_at, updated_at
            FROM calendar_event
            WHERE user_id IN (:userIds)
              AND ends_at > :from
              AND starts_at < :to
            ORDER BY starts_at, ends_at, id
            """)
    List<CalendarEvent> findByUserIdsAndRange(@Param("userIds") List<String> userIds,
                                              @Param("from") OffsetDateTime from,
                                              @Param("to") OffsetDateTime to);


    @Modifying
    @Query("""
            INSERT INTO calendar_event (
                id, user_id, title, description, starts_at, ends_at, timezone,
                all_day, work_item_id, created_by_user_id, created_at, updated_at
            ) VALUES (
                :id, :userId, :title, :description, :startsAt, :endsAt, :timezone,
                :allDay, :workItemId, :createdByUserId, :createdAt, :updatedAt
            )
            """)
    int insert(@Param("id") String id,
               @Param("userId") String userId,
               @Param("title") String title,
               @Param("description") String description,
               @Param("startsAt") OffsetDateTime startsAt,
               @Param("endsAt") OffsetDateTime endsAt,
               @Param("timezone") String timezone,
               @Param("allDay") boolean allDay,
               @Param("workItemId") String workItemId,
               @Param("createdByUserId") String createdByUserId,
               @Param("createdAt") OffsetDateTime createdAt,
               @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            UPDATE calendar_event
            SET title = :title,
                description = :description,
                starts_at = :startsAt,
                ends_at = :endsAt,
                timezone = :timezone,
                all_day = :allDay,
                work_item_id = :workItemId,
                updated_at = :updatedAt
            WHERE id = :id
              AND user_id = :userId
            """)
    int update(@Param("id") String id,
               @Param("userId") String userId,
               @Param("title") String title,
               @Param("description") String description,
               @Param("startsAt") OffsetDateTime startsAt,
               @Param("endsAt") OffsetDateTime endsAt,
               @Param("timezone") String timezone,
               @Param("allDay") boolean allDay,
               @Param("workItemId") String workItemId,
               @Param("updatedAt") OffsetDateTime updatedAt);

    @Modifying
    @Query("DELETE FROM calendar_event WHERE id = :id AND user_id = :userId")
    int delete(@Param("id") String id, @Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM calendar_event WHERE user_id = :userId")
    int deleteByUserId(@Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM calendar_event WHERE work_item_id = :workItemId")
    int deleteByWorkItemId(@Param("workItemId") String workItemId);

    @Modifying
    @Query("DELETE FROM calendar_event WHERE work_item_id IN (SELECT id FROM work_item WHERE project_id = :projectId)")
    int deleteByProjectId(@Param("projectId") String projectId);
}
