package com.windrunner.server.proposal;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ProposalRepository extends CrudRepository<Proposal, String> {
    String COLUMNS = "id, workflow_type, chat_session_id, source_message_id, actor_id, status, reviewed_by_actor_id, reviewed_at, applied_at, created_at, updated_at";

    record AnalyticsRow(long proposalsCreated, long changesProposed, long changesAccepted,
                        long changesRejected, long changesNeedsUpdate, long changesPending) {
        public static AnalyticsRow empty() {
            return new AnalyticsRow(0, 0, 0, 0, 0, 0);
        }
    }

    record AnalyticsEntityRow(String entityType, long changesProposed, long changesAccepted,
                              long changesRejected, long changesNeedsUpdate, long changesPending) {
    }

    @Query("SELECT " + COLUMNS + " FROM proposal WHERE workflow_type = :workflowType AND chat_session_id = :sessionId AND actor_id = :actorId ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
    List<Proposal> page(@Param("workflowType") String workflowType, @Param("sessionId") String sessionId, @Param("actorId") String actorId,
                                @Param("limit") int limit, @Param("offset") int offset);

    @Query("""
            SELECT
                (SELECT COUNT(DISTINCT p.id)
                 FROM proposal p
                 JOIN proposal_change c ON c.proposal_id = p.id
                 WHERE p.created_at >= :since) AS proposals_created,
                COUNT(*) FILTER (WHERE activity_at >= :since) AS changes_proposed,
                COUNT(*) FILTER (WHERE activity_at >= :since AND status = 'APPLIED') AS changes_accepted,
                COUNT(*) FILTER (WHERE activity_at >= :since AND status = 'REJECTED') AS changes_rejected,
                COUNT(*) FILTER (WHERE activity_at >= :since AND status = 'NEEDS_UPDATE') AS changes_needs_update,
                COUNT(*) FILTER (WHERE activity_at >= :since AND status IN ('PENDING', 'APPLYING')) AS changes_pending
            FROM (
                SELECT c.status,
                       CASE WHEN c.status = 'APPLIED'
                            THEN COALESCE(c.applied_at, c.updated_at, c.created_at)
                            ELSE COALESCE(c.updated_at, c.created_at)
                       END AS activity_at
                FROM proposal_change c
                JOIN proposal p ON p.id = c.proposal_id
            ) changes
            """)
    Optional<AnalyticsRow> summarizeAnalytics(@Param("since") OffsetDateTime since);

    @Query("""
            SELECT
                c.entity_type AS entity_type,
                COUNT(*) FILTER (WHERE c.activity_at >= :since) AS changes_proposed,
                COUNT(*) FILTER (WHERE c.activity_at >= :since AND c.status = 'APPLIED') AS changes_accepted,
                COUNT(*) FILTER (WHERE c.activity_at >= :since AND c.status = 'REJECTED') AS changes_rejected,
                COUNT(*) FILTER (WHERE c.activity_at >= :since AND c.status = 'NEEDS_UPDATE') AS changes_needs_update,
                COUNT(*) FILTER (WHERE c.activity_at >= :since AND c.status IN ('PENDING', 'APPLYING')) AS changes_pending
            FROM (
                SELECT c.entity_type, c.status,
                       CASE WHEN c.status = 'APPLIED'
                            THEN COALESCE(c.applied_at, c.updated_at, c.created_at)
                            ELSE COALESCE(c.updated_at, c.created_at)
                       END AS activity_at
                FROM proposal_change c
                JOIN proposal p ON p.id = c.proposal_id
            ) c
            GROUP BY c.entity_type
            """)
    List<AnalyticsEntityRow> summarizeAnalyticsByEntity(@Param("since") OffsetDateTime since);

    @Query("SELECT " + COLUMNS + " FROM proposal WHERE workflow_type = :workflowType AND id = :id AND chat_session_id = :sessionId AND actor_id = :actorId")
    Optional<Proposal> findForDecision(@Param("workflowType") String workflowType, @Param("id") String id, @Param("sessionId") String sessionId,
                                               @Param("actorId") String actorId);

    @Modifying
    @Query("UPDATE proposal SET status = 'APPLYING', updated_at = NOW() WHERE id = :id AND chat_session_id = :sessionId AND actor_id = :actorId AND status = 'PENDING'")
    int claimForDecision(@Param("id") String id, @Param("sessionId") String sessionId, @Param("actorId") String actorId);

    @Modifying
    @Query("INSERT INTO proposal (id, workflow_type, chat_session_id, source_message_id, actor_id, status) VALUES (:id, :workflowType, :sessionId, :messageId, :actorId, 'PENDING')")
    void insertParent(@Param("id") String id, @Param("workflowType") String workflowType,
                      @Param("sessionId") String sessionId, @Param("messageId") String messageId,
                      @Param("actorId") String actorId);

    @Modifying
    @Query("UPDATE proposal SET status = :status, reviewed_by_actor_id = :actorId, reviewed_at = NOW(), applied_at = CASE WHEN :status = 'APPLIED' THEN NOW() ELSE applied_at END, updated_at = NOW() WHERE id = :id AND chat_session_id = :sessionId AND actor_id = :actorId AND status = 'APPLYING'")
    int decide(@Param("id") String id, @Param("sessionId") String sessionId, @Param("actorId") String actorId,
               @Param("status") String status);

    @Modifying
    @Query("DELETE FROM proposal_change WHERE proposal_id IN (SELECT id FROM proposal WHERE chat_session_id = :sessionId)")
    int deleteChangesBySessionId(@Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM proposal WHERE chat_session_id = :sessionId")
    void deleteBySessionId(@Param("sessionId") String sessionId);
}
