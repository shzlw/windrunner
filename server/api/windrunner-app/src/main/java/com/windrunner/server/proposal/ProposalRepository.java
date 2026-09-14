package com.windrunner.server.proposal;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ProposalRepository extends CrudRepository<Proposal, String> {
    String COLUMNS = "id, workflow_type, project_id, source_type, chat_session_id, source_message_id, source_text, source_api_key_id, reverts_proposal_id, actor_id, status, reviewed_by_actor_id, reviewed_at, applied_at, created_at, updated_at";

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

    @Query("SELECT " + COLUMNS + " FROM proposal WHERE workflow_type = 'WORKSPACE' AND project_id = :projectId ORDER BY created_at DESC, id DESC")
    List<Proposal> findWorkspaceByProjectId(@Param("projectId") String projectId);

    @Query("SELECT " + COLUMNS + " FROM proposal WHERE workflow_type = 'WORKSPACE' AND project_id = :projectId AND chat_session_id = :chatSessionId ORDER BY created_at DESC, id DESC")
    List<Proposal> findWorkspaceByProjectIdAndChatSessionId(@Param("projectId") String projectId,
                                                             @Param("chatSessionId") String chatSessionId);

    @Query("SELECT " + COLUMNS + " FROM proposal WHERE workflow_type = 'WORKSPACE' AND id = :id AND project_id = :projectId")
    Optional<Proposal> findWorkspaceInProject(@Param("id") String id, @Param("projectId") String projectId);

    @Query("SELECT " + COLUMNS + " FROM proposal WHERE workflow_type = 'WORKSPACE' AND id = :id AND project_id = :projectId FOR UPDATE")
    Optional<Proposal> findWorkspaceInProjectForUpdate(@Param("id") String id,
                                                        @Param("projectId") String projectId);

    @Query("SELECT " + COLUMNS + " FROM proposal WHERE workflow_type = :workflowType AND id = :id AND chat_session_id = :sessionId AND actor_id = :actorId FOR UPDATE")
    Optional<Proposal> findForRevert(@Param("workflowType") String workflowType, @Param("id") String id,
                                     @Param("sessionId") String sessionId, @Param("actorId") String actorId);

    @Query("SELECT " + COLUMNS + " FROM proposal WHERE reverts_proposal_id = :proposalId AND status IN ('PENDING', 'APPLYING', 'APPLIED', 'COMPLETED') ORDER BY created_at DESC, id DESC LIMIT 1")
    Optional<Proposal> findActiveRevert(@Param("proposalId") String proposalId);

    @Query("""
            SELECT
                (SELECT COUNT(DISTINCT p.id)
                 FROM proposal p
                 JOIN proposal_change c ON c.proposal_id = p.id
                 WHERE p.project_id IS NULL AND p.created_at >= :since) AS proposals_created,
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
                WHERE p.project_id IS NULL
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
                WHERE p.project_id IS NULL
            ) c
            GROUP BY c.entity_type
            """)
    List<AnalyticsEntityRow> summarizeAnalyticsByEntity(@Param("since") OffsetDateTime since);

    @Query("""
            SELECT
                (SELECT COUNT(*) FROM proposal WHERE project_id IN (:projectIds) AND created_at >= :since) AS proposals_created,
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
                WHERE p.project_id IN (:projectIds)
            ) changes
            """)
    Optional<AnalyticsRow> summarizeAnalyticsForProjects(@Param("projectIds") List<String> projectIds,
                                                          @Param("since") OffsetDateTime since);

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
                WHERE p.project_id IN (:projectIds)
            ) c
            GROUP BY c.entity_type
            """)
    List<AnalyticsEntityRow> summarizeAnalyticsByEntityForProjects(@Param("projectIds") List<String> projectIds,
                                                                    @Param("since") OffsetDateTime since);

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
    @Query("INSERT INTO proposal (id, workflow_type, chat_session_id, source_message_id, reverts_proposal_id, actor_id, status) VALUES (:id, :workflowType, :sessionId, :messageId, :revertsProposalId, :actorId, 'PENDING')")
    void insertRevertParent(@Param("id") String id, @Param("workflowType") String workflowType,
                            @Param("sessionId") String sessionId, @Param("messageId") String messageId,
                            @Param("revertsProposalId") String revertsProposalId, @Param("actorId") String actorId);

    @Modifying
    @Query("INSERT INTO proposal (id, workflow_type, project_id, source_type, chat_session_id, source_message_id, source_text, actor_id, status) VALUES (:id, 'WORKSPACE', :projectId, 'CHAT', :sessionId, :messageId, :sourceText, :actorId, 'PENDING')")
    void insertWorkspace(@Param("id") String id, @Param("projectId") String projectId,
                         @Param("sessionId") String sessionId, @Param("messageId") String messageId,
                         @Param("sourceText") String sourceText, @Param("actorId") String actorId);

    @Modifying
    @Query("INSERT INTO proposal (id, workflow_type, project_id, source_type, chat_session_id, source_message_id, source_text, reverts_proposal_id, actor_id, status) VALUES (:id, 'WORKSPACE', :projectId, 'CHAT', :sessionId, :messageId, :sourceText, :revertsProposalId, :actorId, 'PENDING')")
    void insertWorkspaceRevert(@Param("id") String id, @Param("projectId") String projectId,
                               @Param("sessionId") String sessionId, @Param("messageId") String messageId,
                               @Param("sourceText") String sourceText,
                               @Param("revertsProposalId") String revertsProposalId,
                               @Param("actorId") String actorId);

    @Modifying
    @Query("UPDATE proposal SET status = :status, reviewed_by_actor_id = :actorId, reviewed_at = NOW(), applied_at = CASE WHEN :status = 'APPLIED' THEN NOW() ELSE applied_at END, updated_at = NOW() WHERE id = :id AND chat_session_id = :sessionId AND actor_id = :actorId AND status = 'APPLYING'")
    int decide(@Param("id") String id, @Param("sessionId") String sessionId, @Param("actorId") String actorId,
               @Param("status") String status);

    @Modifying
    @Query("UPDATE proposal SET status = :status, reviewed_by_actor_id = CASE WHEN :status = 'PENDING' THEN reviewed_by_actor_id ELSE :actorId END, reviewed_at = CASE WHEN :status = 'PENDING' THEN reviewed_at ELSE NOW() END, applied_at = CASE WHEN :status = 'APPLIED' THEN NOW() ELSE applied_at END, updated_at = NOW() WHERE id = :id AND workflow_type = 'WORKSPACE' AND project_id = :projectId")
    int updateWorkspaceStatus(@Param("id") String id, @Param("projectId") String projectId,
                              @Param("status") String status, @Param("actorId") String actorId);

    @Modifying
    @Query("DELETE FROM proposal_change WHERE proposal_id IN (SELECT id FROM proposal WHERE chat_session_id = :sessionId)")
    int deleteChangesBySessionId(@Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM proposal WHERE chat_session_id = :sessionId")
    void deleteBySessionId(@Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM proposal_change WHERE proposal_id IN (SELECT id FROM proposal WHERE project_id = :projectId)")
    int deleteChangesByProjectId(@Param("projectId") String projectId);

    @Modifying
    @Query("DELETE FROM proposal WHERE project_id = :projectId")
    int deleteByProjectId(@Param("projectId") String projectId);
}
