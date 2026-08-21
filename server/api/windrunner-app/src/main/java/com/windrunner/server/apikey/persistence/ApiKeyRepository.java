package com.windrunner.server.apikey.persistence;

import com.windrunner.server.apikey.domain.ApiKey;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends CrudRepository<ApiKey, String> {

    @Query("""
            SELECT id, owner_user_id, name, key_hash, status, created_at, last_used_at, revoked_at
            FROM api_key
            WHERE owner_user_id = :ownerUserId
            ORDER BY created_at DESC, id DESC
            """)
    List<ApiKey> findByOwnerUserId(@Param("ownerUserId") String ownerUserId);

    @Query("""
            SELECT id, owner_user_id, name, key_hash, status, created_at, last_used_at, revoked_at
            FROM api_key
            WHERE key_hash = :keyHash
              AND status = 'ACTIVE'
            LIMIT 1
            """)
    Optional<ApiKey> findActiveByKeyHash(@Param("keyHash") String keyHash);

    @Modifying
    @Query("""
            INSERT INTO api_key (
                id,
                owner_user_id,
                name,
                key_hash,
                status,
                created_at
            )
            VALUES (
                :id,
                :ownerUserId,
                :name,
                :keyHash,
                :status,
                :createdAt
            )
            """)
    int insertApiKey(@Param("id") String id,
                     @Param("ownerUserId") String ownerUserId,
                     @Param("name") String name,
                     @Param("keyHash") String keyHash,
                     @Param("status") String status,
                     @Param("createdAt") OffsetDateTime createdAt);

    @Modifying
    @Query("""
            UPDATE api_key
            SET status = 'REVOKED',
                revoked_at = :revokedAt
            WHERE id = :id
              AND owner_user_id = :ownerUserId
              AND status = 'ACTIVE'
            """)
    int revokeOwnedApiKey(@Param("id") String id,
                          @Param("ownerUserId") String ownerUserId,
                          @Param("revokedAt") OffsetDateTime revokedAt);

    @Modifying
    @Query("""
            UPDATE api_key
            SET last_used_at = :lastUsedAt
            WHERE id = :id
            """)
    int updateLastUsedAt(@Param("id") String id,
                         @Param("lastUsedAt") OffsetDateTime lastUsedAt);
}
