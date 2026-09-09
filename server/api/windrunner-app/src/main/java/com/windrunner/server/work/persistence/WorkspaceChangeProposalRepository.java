package com.windrunner.server.work.persistence;

import com.windrunner.server.work.domain.WorkspaceChangeProposal;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceChangeProposalRepository extends CrudRepository<WorkspaceChangeProposal, String> {
    String COLUMNS = "id, project_id, chat_session_id, source_message_id, source_text, status, created_at, updated_at";

    record AnalyticsRow(long proposalsCreated, long changesProposed, long changesAccepted,
                        long changesRejected, long changesNeedsUpdate, long changesPending) {
        public static AnalyticsRow empty() {
            return new AnalyticsRow(0, 0, 0, 0, 0, 0);
        }
    }

    record AnalyticsEntityRow(String entityType, long changesProposed, long changesAccepted,
                              long changesRejected, long changesNeedsUpdate, long changesPending) {
    }

    @Query("SELECT " + COLUMNS + " FROM workspace_change_proposal WHERE project_id = :projectId ORDER BY created_at DESC, id DESC")
    List<WorkspaceChangeProposal> findByProjectId(@Param("projectId") String projectId);

    @Query("SELECT " + COLUMNS + " FROM workspace_change_proposal WHERE project_id = :projectId AND chat_session_id = :chatSessionId ORDER BY created_at DESC, id DESC")
    List<WorkspaceChangeProposal> findByProjectIdAndChatSessionId(@Param("projectId") String projectId,
                                                                  @Param("chatSessionId") String chatSessionId);

    @Query("SELECT " + COLUMNS + " FROM workspace_change_proposal WHERE id = :id AND project_id = :projectId FOR UPDATE")
    Optional<WorkspaceChangeProposal> findInProjectForUpdate(@Param("id") String id,
                                                              @Param("projectId") String projectId);

    @Query("""
            SELECT
                (SELECT COUNT(*) FROM workspace_change_proposal WHERE created_at >= :since) AS proposals_created,
                COUNT(*) FILTER (WHERE activity_at >= :since) AS changes_proposed,
                COUNT(*) FILTER (WHERE activity_at >= :since AND status = 'APPLIED') AS changes_accepted,
                COUNT(*) FILTER (WHERE activity_at >= :since AND status = 'REJECTED') AS changes_rejected,
                COUNT(*) FILTER (WHERE activity_at >= :since AND status = 'NEEDS_UPDATE') AS changes_needs_update,
                COUNT(*) FILTER (WHERE activity_at >= :since AND status = 'PENDING') AS changes_pending
            FROM (
                SELECT c.status,
                       CASE WHEN c.status = 'APPLIED'
                            THEN COALESCE(c.applied_at, c.updated_at, c.created_at)
                            ELSE COALESCE(c.updated_at, c.created_at)
                       END AS activity_at
                FROM workspace_change c
                JOIN workspace_change_proposal p ON p.id = c.proposal_id
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
                COUNT(*) FILTER (WHERE c.activity_at >= :since AND c.status = 'PENDING') AS changes_pending
            FROM (
                SELECT c.entity_type, c.status,
                       CASE WHEN c.status = 'APPLIED'
                            THEN COALESCE(c.applied_at, c.updated_at, c.created_at)
                            ELSE COALESCE(c.updated_at, c.created_at)
                       END AS activity_at
                FROM workspace_change c
                JOIN workspace_change_proposal p ON p.id = c.proposal_id
            ) c
            GROUP BY c.entity_type
            """)
    List<AnalyticsEntityRow> summarizeAnalyticsByEntity(@Param("since") OffsetDateTime since);

    @Query("""
            SELECT
                (SELECT COUNT(*) FROM workspace_change_proposal WHERE project_id IN (:projectIds) AND created_at >= :since) AS proposals_created,
                COUNT(*) FILTER (WHERE activity_at >= :since) AS changes_proposed,
                COUNT(*) FILTER (WHERE activity_at >= :since AND status = 'APPLIED') AS changes_accepted,
                COUNT(*) FILTER (WHERE activity_at >= :since AND status = 'REJECTED') AS changes_rejected,
                COUNT(*) FILTER (WHERE activity_at >= :since AND status = 'NEEDS_UPDATE') AS changes_needs_update,
                COUNT(*) FILTER (WHERE activity_at >= :since AND status = 'PENDING') AS changes_pending
            FROM (
                SELECT c.status,
                       CASE WHEN c.status = 'APPLIED'
                            THEN COALESCE(c.applied_at, c.updated_at, c.created_at)
                            ELSE COALESCE(c.updated_at, c.created_at)
                       END AS activity_at
                FROM workspace_change c
                JOIN workspace_change_proposal p ON p.id = c.proposal_id
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
                COUNT(*) FILTER (WHERE c.activity_at >= :since AND c.status = 'PENDING') AS changes_pending
            FROM (
                SELECT c.entity_type, c.status,
                       CASE WHEN c.status = 'APPLIED'
                            THEN COALESCE(c.applied_at, c.updated_at, c.created_at)
                            ELSE COALESCE(c.updated_at, c.created_at)
                       END AS activity_at
                FROM workspace_change c
                JOIN workspace_change_proposal p ON p.id = c.proposal_id
                WHERE p.project_id IN (:projectIds)
            ) c
            GROUP BY c.entity_type
            """)
    List<AnalyticsEntityRow> summarizeAnalyticsByEntityForProjects(@Param("projectIds") List<String> projectIds,
                                                                    @Param("since") OffsetDateTime since);

    @Query("SELECT " + COLUMNS + " FROM workspace_change_proposal WHERE id = :id AND project_id = :projectId")
    Optional<WorkspaceChangeProposal> findInProject(@Param("id") String id, @Param("projectId") String projectId);

    @Modifying
    @Query("INSERT INTO workspace_change_proposal (id, project_id, chat_session_id, source_message_id, source_text, status) VALUES (:id, :projectId, :chatSessionId, :sourceMessageId, :sourceText, 'PENDING')")
    void insert(@Param("id") String id, @Param("projectId") String projectId, @Param("chatSessionId") String chatSessionId,
                @Param("sourceMessageId") String sourceMessageId, @Param("sourceText") String sourceText);

    @Modifying
    @Query("UPDATE workspace_change_proposal SET status = :status, updated_at = NOW() WHERE id = :id AND project_id = :projectId")
    int updateStatus(@Param("id") String id, @Param("projectId") String projectId, @Param("status") String status);

    @Modifying
    @Query("DELETE FROM workspace_change_proposal WHERE chat_session_id = :chatSessionId")
    int deleteByChatSessionId(@Param("chatSessionId") String chatSessionId);

    @Modifying
    @Query("DELETE FROM workspace_change_proposal WHERE project_id = :projectId")
    int deleteByProjectId(@Param("projectId") String projectId);
}
