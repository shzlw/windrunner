package com.windrunner.server.chat.persistence;

import com.windrunner.server.chat.domain.ChatMessage;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends CrudRepository<ChatMessage, String> {

    @Query("""
            SELECT id, chat_session_id, role, content, created_at
            FROM chat_message
            WHERE chat_session_id = :chatSessionId
            ORDER BY created_at ASC, id ASC
            """)
    List<ChatMessage> findBySessionIdOrdered(@Param("chatSessionId") String chatSessionId);

    @Query("""
            SELECT id, chat_session_id, role, content, created_at
            FROM (
                SELECT id, chat_session_id, role, content, created_at
                FROM chat_message
                WHERE chat_session_id = :chatSessionId
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
            ) recent
            ORDER BY created_at ASC, id ASC
            """)
    List<ChatMessage> findRecentBySessionId(@Param("chatSessionId") String chatSessionId,
                                            @Param("limit") int limit);

    @Query("""
            SELECT id, chat_session_id, role, content, created_at
            FROM (
                SELECT message.id, message.chat_session_id, message.role, message.content, message.created_at,
                       COALESCE(assistant_source.created_at, message.created_at) AS turn_created_at,
                       COALESCE(source_request.ingestion_sequence, assistant_request.ingestion_sequence, 0) AS turn_sequence,
                       CASE WHEN assistant_request.id IS NULL THEN 0 ELSE 1 END AS turn_order
                FROM chat_message message
                LEFT JOIN agent_message_route source_request
                    ON source_request.source_message_id = message.id
                LEFT JOIN agent_message_route assistant_request
                    ON assistant_request.assistant_message_id = message.id
                LEFT JOIN chat_message assistant_source
                    ON assistant_source.id = assistant_request.source_message_id
                WHERE message.chat_session_id = :chatSessionId
                  AND (
                       source_request.ingestion_sequence <= :ingestionSequence
                    OR assistant_request.ingestion_sequence < :ingestionSequence
                    OR (
                        source_request.id IS NULL
                        AND assistant_request.id IS NULL
                        AND message.created_at <= :sourceCreatedAt
                    )
                  )
                ORDER BY turn_created_at DESC, turn_sequence DESC, turn_order DESC, message.id DESC
                LIMIT :limit
            ) history
            ORDER BY turn_created_at ASC, turn_sequence ASC, turn_order ASC, id ASC
            """)
    List<ChatMessage> findForAgentRequest(@Param("chatSessionId") String chatSessionId,
                                          @Param("ingestionSequence") long ingestionSequence,
                                          @Param("sourceCreatedAt") OffsetDateTime sourceCreatedAt,
                                          @Param("limit") int limit);

    @Query("""
            SELECT DISTINCT ON (chat_session_id) id, chat_session_id, role, content, created_at
            FROM chat_message
            WHERE chat_session_id IN (:chatSessionIds) AND role = 'user'
            ORDER BY chat_session_id, created_at ASC, id ASC
            """)
    List<ChatMessage> findFirstUserMessages(@Param("chatSessionIds") List<String> chatSessionIds);

    @Query("""
            SELECT id, chat_session_id, role, content, created_at
            FROM chat_message
            WHERE id = :id AND chat_session_id = :chatSessionId
            """)
    Optional<ChatMessage> findByIdAndSessionId(@Param("id") String id,
                                               @Param("chatSessionId") String chatSessionId);

    @Modifying
    @Query("""
            INSERT INTO chat_message (id, chat_session_id, role, content)
            VALUES (:id, :chatSessionId, :role, :content)
            """)
    void insert(@Param("id") String id,
                @Param("chatSessionId") String chatSessionId,
                @Param("role") String role,
                @Param("content") String content);

    @Modifying
    @Query("DELETE FROM chat_message WHERE chat_session_id = :chatSessionId")
    int deleteBySessionId(@Param("chatSessionId") String chatSessionId);
}
