package com.windrunner.server.llm.persistence;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LlmUsageRepository extends CrudRepository<com.windrunner.server.llm.domain.LlmUsage, String> {

    record TotalsRow(long requests, long inputTokens, long outputTokens, long successes, double avgDurationMs) {
    }

    record ProjectRow(String projectId, long requests, long inputTokens, long outputTokens, long successes,
                      double avgDurationMs) {
    }

    record FeatureRow(String feature, long requests, long inputTokens, long outputTokens, long successes) {
    }

    record ProviderRow(String provider, String model, long requests, long inputTokens, long outputTokens,
                       long successes) {
    }

    @Query("""
            SELECT
                COUNT(*) AS requests,
                COALESCE(SUM(input_tokens), 0) AS input_tokens,
                COALESCE(SUM(output_tokens), 0) AS output_tokens,
                COALESCE(SUM(CASE WHEN outcome = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS successes,
                COALESCE(AVG(duration_ms), 0)::float8 AS avg_duration_ms
            FROM llm_usage
            WHERE project_id IN (:projectIds)
              AND created_at >= :since
            """)
    Optional<TotalsRow> summarizeTotals(@Param("projectIds") List<String> projectIds,
                                        @Param("since") OffsetDateTime since);

    @Query("""
            SELECT
                project_id AS project_id,
                COUNT(*) AS requests,
                COALESCE(SUM(input_tokens), 0) AS input_tokens,
                COALESCE(SUM(output_tokens), 0) AS output_tokens,
                COALESCE(SUM(CASE WHEN outcome = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS successes,
                COALESCE(AVG(duration_ms), 0)::float8 AS avg_duration_ms
            FROM llm_usage
            WHERE project_id IN (:projectIds)
              AND created_at >= :since
            GROUP BY project_id
            ORDER BY requests DESC, project_id ASC
            """)
    List<ProjectRow> summarizeByProject(@Param("projectIds") List<String> projectIds,
                                        @Param("since") OffsetDateTime since);

    @Query("""
            SELECT
                feature AS feature,
                COUNT(*) AS requests,
                COALESCE(SUM(input_tokens), 0) AS input_tokens,
                COALESCE(SUM(output_tokens), 0) AS output_tokens,
                COALESCE(SUM(CASE WHEN outcome = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS successes
            FROM llm_usage
            WHERE project_id IN (:projectIds)
              AND created_at >= :since
            GROUP BY feature
            ORDER BY requests DESC, feature ASC
            """)
    List<FeatureRow> summarizeByFeature(@Param("projectIds") List<String> projectIds,
                                        @Param("since") OffsetDateTime since);

    @Query("""
            SELECT
                provider AS provider,
                model AS model,
                COUNT(*) AS requests,
                COALESCE(SUM(input_tokens), 0) AS input_tokens,
                COALESCE(SUM(output_tokens), 0) AS output_tokens,
                COALESCE(SUM(CASE WHEN outcome = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS successes
            FROM llm_usage
            WHERE project_id IN (:projectIds)
              AND created_at >= :since
            GROUP BY provider, model
            ORDER BY requests DESC, provider ASC, model ASC
            """)
    List<ProviderRow> summarizeByProviderModel(@Param("projectIds") List<String> projectIds,
                                               @Param("since") OffsetDateTime since);

    @Modifying
    @Query("""
            INSERT INTO llm_usage (
                id,
                user_id,
                project_id,
                feature,
                provider,
                model,
                input_tokens,
                output_tokens,
                total_tokens,
                outcome,
                error_message,
                duration_ms
            )
            VALUES (
                :id,
                :userId,
                :projectId,
                :feature,
                :provider,
                :model,
                :inputTokens,
                :outputTokens,
                :totalTokens,
                :outcome,
                :errorMessage,
                :durationMs
            )
            """)
    void insert(@Param("id") String id,
                @Param("userId") String userId,
                @Param("projectId") String projectId,
                @Param("feature") String feature,
                @Param("provider") String provider,
                @Param("model") String model,
                @Param("inputTokens") Long inputTokens,
                @Param("outputTokens") Long outputTokens,
                @Param("totalTokens") Long totalTokens,
                @Param("outcome") String outcome,
                @Param("errorMessage") String errorMessage,
                @Param("durationMs") Long durationMs);
}