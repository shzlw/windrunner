package com.windrunner.server.auth.persistence;

import com.windrunner.server.auth.domain.AuthSession;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface AuthSessionRepository extends CrudRepository<AuthSession, String> {

    @Modifying
    @Query("""
            INSERT INTO auth_session (
                id,
                user_id,
                session_token_hash,
                csrf_token,
                expires_at,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :userId,
                :sessionTokenHash,
                :csrfToken,
                :expiresAt,
                :createdAt,
                :updatedAt
            )
            """)
    int insertSession(@Param("id") String id,
                      @Param("userId") String userId,
                      @Param("sessionTokenHash") String sessionTokenHash,
                      @Param("csrfToken") String csrfToken,
                      @Param("expiresAt") OffsetDateTime expiresAt,
                      @Param("createdAt") OffsetDateTime createdAt,
                      @Param("updatedAt") OffsetDateTime updatedAt);

    @Query("""
            SELECT *
            FROM auth_session
            WHERE session_token_hash = :sessionTokenHash
              AND expires_at > :now
            LIMIT 1
            """)
    Optional<AuthSession> findActiveBySessionTokenHash(@Param("sessionTokenHash") String sessionTokenHash,
                                                       @Param("now") OffsetDateTime now);

    @Modifying
    @Query("""
            DELETE FROM auth_session
            WHERE session_token_hash = :sessionTokenHash
            """)
    int deleteBySessionTokenHash(@Param("sessionTokenHash") String sessionTokenHash);

    @Modifying
    @Query("""
            DELETE FROM auth_session
            WHERE user_id = :userId
            """)
    int deleteByUserId(@Param("userId") String userId);

    @Modifying
    @Query("""
            DELETE FROM auth_session
            WHERE expires_at <= :now
            """)
    int deleteExpired(@Param("now") OffsetDateTime now);
}
