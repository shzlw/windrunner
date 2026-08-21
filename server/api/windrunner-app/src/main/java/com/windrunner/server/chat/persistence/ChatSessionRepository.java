package com.windrunner.server.chat.persistence;

import com.windrunner.server.chat.domain.ChatSession;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatSessionRepository extends CrudRepository<ChatSession, String> {

    @Query("""
            SELECT id, project_id, user_id, status, created_at, updated_at, archived_at
            FROM chat_session
            WHERE project_id = :projectId AND user_id = :userId AND status = 'ACTIVE'
            """)
    Optional<ChatSession> findActive(@Param("projectId") String projectId,
                                     @Param("userId") String userId);

    @Query("""
            SELECT id, project_id, user_id, status, created_at, updated_at, archived_at
            FROM chat_session
            WHERE id = :id AND project_id = :projectId
            """)
    Optional<ChatSession> findByIdAndProjectId(@Param("id") String id,
                                               @Param("projectId") String projectId);

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
            SET status = 'ARCHIVED', archived_at = NOW(), updated_at = NOW()
            WHERE project_id = :projectId AND user_id = :userId AND status = 'ACTIVE'
            """)
    int archiveActive(@Param("projectId") String projectId, @Param("userId") String userId);
}
