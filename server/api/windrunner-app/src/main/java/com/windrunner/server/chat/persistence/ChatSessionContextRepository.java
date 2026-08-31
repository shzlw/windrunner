package com.windrunner.server.chat.persistence;

import com.windrunner.server.chat.domain.ChatSessionContext;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionContextRepository extends CrudRepository<ChatSessionContext, String> {
    @Query("""
            SELECT id, chat_session_id, entity_type, entity_id, created_at
            FROM chat_session_context
            WHERE chat_session_id = :sessionId
            ORDER BY created_at ASC, id ASC
            """)
    List<ChatSessionContext> findBySessionId(@Param("sessionId") String sessionId);

    @Query("""
            SELECT id, chat_session_id, entity_type, entity_id, created_at
            FROM chat_session_context
            WHERE id = :id AND chat_session_id = :sessionId
            """)
    Optional<ChatSessionContext> findByIdAndSessionId(@Param("id") String id, @Param("sessionId") String sessionId);

    @Modifying
    @Query("""
            INSERT INTO chat_session_context (id, chat_session_id, entity_type, entity_id)
            VALUES (:id, :sessionId, :entityType, :entityId)
            ON CONFLICT (chat_session_id, entity_type, entity_id) DO NOTHING
            """)
    int insert(@Param("id") String id, @Param("sessionId") String sessionId,
               @Param("entityType") String entityType, @Param("entityId") String entityId);

    @Modifying
    @Query("DELETE FROM chat_session_context WHERE id = :id AND chat_session_id = :sessionId")
    int delete(@Param("id") String id, @Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM chat_session_context WHERE chat_session_id = :sessionId")
    int deleteBySessionId(@Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM chat_session_context WHERE entity_type = :entityType AND entity_id = :entityId")
    int deleteByEntity(@Param("entityType") String entityType, @Param("entityId") String entityId);
}
