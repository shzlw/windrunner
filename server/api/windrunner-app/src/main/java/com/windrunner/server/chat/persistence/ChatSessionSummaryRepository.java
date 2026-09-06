package com.windrunner.server.chat.persistence;

import com.windrunner.server.chat.domain.ChatSessionSummary;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface ChatSessionSummaryRepository extends CrudRepository<ChatSessionSummary, String> {

    @Query("""
            SELECT id, chat_session_id, summary, summarized_through_message_id,
                   summarized_through_created_at, created_at, updated_at
            FROM chat_session_summary
            WHERE chat_session_id = :sessionId
            """)
    Optional<ChatSessionSummary> findByChatSessionId(@Param("sessionId") String sessionId);

    @Modifying
    @Query("""
            INSERT INTO chat_session_summary (
                id, chat_session_id, summary, summarized_through_message_id,
                summarized_through_created_at
            )
            VALUES (
                :id, :sessionId, :summary, :messageId,
                :messageCreatedAt
            )
            """)
    void insert(@Param("id") String id,
                @Param("sessionId") String sessionId,
                @Param("summary") String summary,
                @Param("messageId") String messageId,
                @Param("messageCreatedAt") OffsetDateTime messageCreatedAt);

    @Modifying
    @Query("""
            UPDATE chat_session_summary
            SET summary = :summary,
                summarized_through_message_id = :messageId,
                summarized_through_created_at = :messageCreatedAt,
                updated_at = NOW()
            WHERE id = :id
            """)
    int update(@Param("id") String id,
               @Param("summary") String summary,
               @Param("messageId") String messageId,
               @Param("messageCreatedAt") OffsetDateTime messageCreatedAt);

    @Modifying
    @Query("DELETE FROM chat_session_summary WHERE chat_session_id = :sessionId")
    int deleteBySessionId(@Param("sessionId") String sessionId);
}
