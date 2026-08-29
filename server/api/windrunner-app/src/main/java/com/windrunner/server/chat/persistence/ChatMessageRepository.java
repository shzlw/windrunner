package com.windrunner.server.chat.persistence;

import com.windrunner.server.chat.domain.ChatMessage;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
