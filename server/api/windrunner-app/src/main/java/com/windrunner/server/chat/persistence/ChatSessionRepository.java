package com.windrunner.server.chat.persistence;

import com.windrunner.server.chat.domain.ChatSession;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends CrudRepository<ChatSession, String> {

    @Query("""
            SELECT id, user_id, title, status, created_at, updated_at, archived_at
            FROM chat_session
            WHERE id = :id AND user_id = :userId
            """)
    Optional<ChatSession> findByIdAndUserId(@Param("id") String id,
                                            @Param("userId") String userId);

    @Query("""
            SELECT id, user_id, title, status, created_at, updated_at, archived_at
            FROM chat_session
            WHERE user_id = :userId
              AND status = 'ACTIVE'
            ORDER BY updated_at DESC NULLS LAST, id DESC
            LIMIT 1
            """)
    Optional<ChatSession> findLatestActiveByUserId(@Param("userId") String userId);

    @Query("""
            SELECT id, user_id, title, status, created_at, updated_at, archived_at
            FROM chat_session
            WHERE user_id = :userId
              AND status = 'ACTIVE'
            ORDER BY updated_at DESC NULLS LAST, id DESC
            LIMIT :limit
            """)
    List<ChatSession> findRecentActiveByUserId(@Param("userId") String userId,
                                               @Param("limit") int limit);

    @Query("""
            SELECT id, user_id, title, status, created_at, updated_at, archived_at
            FROM chat_session
            WHERE user_id = :userId
              AND (
                    :query IS NULL
                 OR :query = ''
                 OR LOWER(COALESCE(title, '')) LIKE CONCAT('%', LOWER(:query), '%')
                 OR EXISTS (
                     SELECT 1
                     FROM chat_message first_message
                     WHERE first_message.chat_session_id = chat_session.id
                       AND first_message.role = 'user'
                       AND LOWER(first_message.content) LIKE CONCAT('%', LOWER(:query), '%')
                 )
              )
            ORDER BY updated_at DESC NULLS LAST, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<ChatSession> findPageByUserId(@Param("userId") String userId,
                                       @Param("query") String query,
                                       @Param("limit") int limit,
                                       @Param("offset") long offset);

    @Modifying
    @Query("""
            INSERT INTO chat_session (id, user_id, status)
            VALUES (:id, :userId, 'ACTIVE')
            """)
    void insert(@Param("id") String id, @Param("userId") String userId);

    @Modifying
    @Query("""
            UPDATE chat_session
            SET updated_at = NOW()
            WHERE id = :id
            """)
    void touch(@Param("id") String id);

    @Modifying
    @Query("""
            UPDATE chat_session
            SET title = :title,
                updated_at = NOW()
            WHERE id = :sessionId
              AND (title IS NULL OR BTRIM(title) = '')
              AND NOT EXISTS (
                  SELECT 1
                  FROM chat_message
                  WHERE chat_session_id = :sessionId
                    AND role = 'user'
                    AND id <> :messageId
              )
            """)
    int setTitleFromFirstMessage(@Param("sessionId") String sessionId,
                                 @Param("messageId") String messageId,
                                 @Param("title") String title);

    @Modifying
    @Query("""
            UPDATE chat_session
            SET title = :title
            WHERE id = :id AND user_id = :userId
            """)
    int updateTitle(@Param("id") String id, @Param("userId") String userId, @Param("title") String title);

    @Modifying
    @Query("DELETE FROM chat_session WHERE id = :id AND user_id = :userId")
    int deleteSession(@Param("id") String id, @Param("userId") String userId);
}
