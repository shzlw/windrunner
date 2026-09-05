package com.windrunner.server.agent.persistence;

import com.windrunner.server.agent.domain.AgentMessageRoute;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentMessageRequestRepository extends CrudRepository<AgentMessageRoute, String> {
    // Call inside a short transaction, before insertion or routing claims.
    @Query("SELECT true FROM pg_advisory_xact_lock(hashtextextended('agent-intake:' || :userId, 0))")
    boolean lockIntake(@Param("userId") String userId);

    @Query("SELECT pg_try_advisory_xact_lock(hashtextextended(:queueKey, 0))")
    boolean tryLockQueue(@Param("queueKey") String queueKey);

    @Query("SELECT * FROM agent_message_route WHERE user_id = :userId AND idempotency_key = :key")
    Optional<AgentMessageRoute> findByKey(@Param("userId") String userId, @Param("key") String key);

    @Query("SELECT * FROM agent_message_route WHERE id = :id AND user_id = :userId")
    Optional<AgentMessageRoute> findForUser(@Param("id") String id, @Param("userId") String userId);

    @Query("""
            SELECT * FROM agent_message_route
            WHERE id = :id AND user_id = :userId AND status = :status
              AND lease_until > clock_timestamp()
            FOR UPDATE
            """)
    Optional<AgentMessageRoute> lockOwned(@Param("id") String id, @Param("userId") String userId,
                                         @Param("status") String status);

    @Query("""
            SELECT r.* FROM agent_message_route r
            WHERE r.status = 'RECEIVED'
              AND NOT EXISTS (
                  SELECT 1 FROM agent_message_route earlier
                  WHERE earlier.user_id = r.user_id
                    AND earlier.ingestion_sequence < r.ingestion_sequence
                    AND earlier.status IN ('RECEIVED', 'ROUTING')
              )
            ORDER BY r.ingestion_sequence
            LIMIT :limit
            """)
    List<AgentMessageRoute> findRoutingCandidates(@Param("limit") int limit);

    @Query("""
            SELECT r.* FROM agent_message_route r
            WHERE r.status = 'ROUTED'
              AND NOT EXISTS (
                  SELECT 1 FROM agent_message_route earlier
                  WHERE earlier.routed_chat_session_id = r.routed_chat_session_id
                    AND earlier.ingestion_sequence < r.ingestion_sequence
                    AND earlier.status IN ('ROUTED', 'PROCESSING')
              )
            ORDER BY r.ingestion_sequence
            LIMIT :limit
            """)
    List<AgentMessageRoute> findProcessingCandidates(@Param("limit") int limit);

    @Query("""
            SELECT routed_chat_session_id FROM agent_message_route
            WHERE user_id = :userId AND routed_chat_session_id IS NOT NULL
            ORDER BY ingestion_sequence DESC LIMIT 1
            """)
    Optional<String> findLastRoutedSession(@Param("userId") String userId);

    @Modifying
    @Query("""
            INSERT INTO agent_message_route (id, user_id, idempotency_key, message)
            VALUES (:id, :userId, :idempotencyKey, :message)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """)
    int insert(@Param("id") String id,
               @Param("userId") String userId,
               @Param("idempotencyKey") String idempotencyKey,
               @Param("message") String message);

    @Modifying
    @Query("""
            UPDATE agent_message_route current_route
            SET status = 'ROUTING',
                lease_until = clock_timestamp() + (:leaseSeconds * INTERVAL '1 second')
            WHERE current_route.id = :id
              AND current_route.user_id = :userId
              AND current_route.status = 'RECEIVED'
              AND NOT EXISTS (
                  SELECT 1
                  FROM agent_message_route earlier_route
                  WHERE earlier_route.user_id = current_route.user_id
                    AND earlier_route.ingestion_sequence < current_route.ingestion_sequence
                    AND earlier_route.status IN ('RECEIVED', 'ROUTING')
              )
            """)
    int claimRouting(@Param("id") String id,
                     @Param("userId") String userId, @Param("leaseSeconds") long leaseSeconds);

    @Modifying
    @Query("""
            UPDATE agent_message_route current_route
            SET routed_chat_session_id = :chatSessionId,
                routing_decision = :routingDecision,
                source_message_id = :sourceMessageId,
                context_ids = :contextIds,
                status = 'ROUTED',
                lease_until = NULL
            WHERE current_route.id = :id
              AND current_route.status = 'ROUTING'
            """)
    int markRouted(@Param("id") String id,
                   @Param("chatSessionId") String chatSessionId,
                   @Param("routingDecision") String routingDecision,
                   @Param("sourceMessageId") String sourceMessageId,
                   @Param("contextIds") String[] contextIds);

    @Modifying
    @Query("""
            UPDATE agent_message_route current_route
            SET status = 'PROCESSING',
                lease_until = clock_timestamp() + (:leaseSeconds * INTERVAL '1 second')
            WHERE current_route.id = :id
              AND current_route.user_id = :userId
              AND current_route.ingestion_sequence = :ingestionSequence
              AND current_route.status = 'ROUTED'
              AND NOT EXISTS (
                  SELECT 1
                  FROM agent_message_route earlier_route
                  WHERE earlier_route.user_id = current_route.user_id
                    AND earlier_route.routed_chat_session_id = current_route.routed_chat_session_id
                    AND earlier_route.ingestion_sequence < current_route.ingestion_sequence
                    AND earlier_route.status IN ('ROUTED', 'PROCESSING')
              )
            """)
    int claimProcessing(@Param("id") String id,
                        @Param("userId") String userId,
                        @Param("ingestionSequence") long ingestionSequence,
                        @Param("leaseSeconds") long leaseSeconds);

    @Modifying
    @Query("""
            UPDATE agent_message_route
            SET assistant_message_id = :assistantMessageId,
                status = 'COMPLETED',
                lease_until = NULL,
                completed_at = NOW()
            WHERE id = :id
              AND status = 'PROCESSING'
            """)
    int markCompleted(@Param("id") String id,
                      @Param("assistantMessageId") String assistantMessageId);

    @Modifying
    @Query("""
            UPDATE agent_message_route
            SET status = 'FAILED',
                lease_until = NULL,
                last_error = :error,
                completed_at = NOW()
            WHERE id = :id
              AND status = :status
            """)
    int markFailed(@Param("id") String id, @Param("status") String status, @Param("error") String error);

    @Modifying
    @Query("""
            UPDATE agent_message_route
            SET status = 'FAILED', lease_until = NULL, completed_at = NOW(),
                last_error = 'Processing was interrupted or timed out'
            WHERE status IN ('ROUTING', 'PROCESSING') AND lease_until <= clock_timestamp()
            """)
    int failExpired();

    @Modifying
    @Query("DELETE FROM agent_message_route WHERE routed_chat_session_id = :chatSessionId")
    int deleteByChatSessionId(@Param("chatSessionId") String chatSessionId);
}
