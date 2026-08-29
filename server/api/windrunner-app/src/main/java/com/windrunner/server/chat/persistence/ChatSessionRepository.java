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
            SELECT id, project_id, user_id, title, status, created_at, updated_at, archived_at
            FROM chat_session
            WHERE project_id = :projectId AND user_id = :userId AND status = 'ACTIVE'
            """)
    Optional<ChatSession> findActive(@Param("projectId") String projectId,
                                     @Param("userId") String userId);

    @Query("""
            SELECT id, project_id, user_id, title, status, created_at, updated_at, archived_at
            FROM chat_session
            WHERE id = :id AND project_id = :projectId
            """)
    Optional<ChatSession> findByIdAndProjectId(@Param("id") String id,
                                               @Param("projectId") String projectId);

    @Query("""
            SELECT id, project_id, user_id, title, status, created_at, updated_at, archived_at
            FROM chat_session
            WHERE id = :id AND project_id = :projectId AND user_id = :userId
            """)
    Optional<ChatSession> findByIdAndProjectIdAndUserId(@Param("id") String id,
                                                        @Param("projectId") String projectId,
                                                        @Param("userId") String userId);

    @Query("""
            SELECT id, project_id, user_id, title, status, created_at, updated_at, archived_at
            FROM chat_session
            WHERE project_id = :projectId AND user_id = :userId
            ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, updated_at DESC, id DESC
            LIMIT 100
            """)
    List<ChatSession> findAllByProjectIdAndUserIdOrdered(@Param("projectId") String projectId,
                                                         @Param("userId") String userId);

    @Modifying
    @Query("""
            INSERT INTO chat_session (id, project_id, user_id, status)
            VALUES (:id, :projectId, :userId, 'ACTIVE')
            """)
    void insert(@Param("id") String id,
                @Param("projectId") String projectId,
                @Param("userId") String userId);

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
            SET status = 'ARCHIVED', archived_at = NOW(), updated_at = NOW()
            WHERE project_id = :projectId AND user_id = :userId AND status = 'ACTIVE'
            """)
    int archiveActive(@Param("projectId") String projectId, @Param("userId") String userId);

    @Modifying
    @Query("""
            UPDATE chat_session
            SET title = :title
            WHERE id = :id AND project_id = :projectId AND user_id = :userId
            """)
    int updateTitle(@Param("id") String id,
                    @Param("projectId") String projectId,
                    @Param("userId") String userId,
                    @Param("title") String title);

    @Modifying
    @Query("DELETE FROM chat_session WHERE id = :id AND project_id = :projectId AND user_id = :userId")
    int deleteSession(@Param("id") String id,
                      @Param("projectId") String projectId,
                      @Param("userId") String userId);
}
