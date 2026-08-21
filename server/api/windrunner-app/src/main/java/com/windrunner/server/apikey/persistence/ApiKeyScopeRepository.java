package com.windrunner.server.apikey.persistence;

import com.windrunner.server.apikey.domain.ApiKeyScope;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

@org.springframework.stereotype.Repository
public interface ApiKeyScopeRepository extends Repository<ApiKeyScope, String> {

    @Query("""
            SELECT scope
            FROM api_key_scope
            WHERE api_key_id = :apiKeyId
            ORDER BY scope ASC
            """)
    List<String> findScopesByApiKeyId(@Param("apiKeyId") String apiKeyId);

    @Modifying
    @Query("""
            INSERT INTO api_key_scope (
                api_key_id,
                scope
            )
            VALUES (
                :apiKeyId,
                :scope
            )
            """)
    int insertScope(@Param("apiKeyId") String apiKeyId,
                    @Param("scope") String scope);
}
