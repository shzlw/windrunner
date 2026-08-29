package com.windrunner.server.work.persistence;

import com.windrunner.server.work.domain.WorkspaceChange;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceChangeRepository extends CrudRepository<WorkspaceChange, String> {
    String COLUMNS = "id, proposal_id, project_id, sort_index, entity_type, action, target_id, summary, payload_json::text AS payload_json, previous_json::text AS previous_json, status, feedback, applied_at, created_at, updated_at";

    @Query("SELECT " + COLUMNS + " FROM workspace_change WHERE proposal_id = :proposalId ORDER BY sort_index, id")
    List<WorkspaceChange> findByProposalId(@Param("proposalId") String proposalId);

    @Query("SELECT " + COLUMNS + " FROM workspace_change WHERE id = :id AND proposal_id = :proposalId AND project_id = :projectId")
    Optional<WorkspaceChange> findInProposal(@Param("id") String id, @Param("proposalId") String proposalId, @Param("projectId") String projectId);

    @Modifying
    @Query("INSERT INTO workspace_change (id, proposal_id, project_id, sort_index, entity_type, action, target_id, summary, payload_json, previous_json, status) VALUES (:id, :proposalId, :projectId, :sortIndex, :entityType, :action, :targetId, :summary, CAST(:payloadJson AS jsonb), CAST(:previousJson AS jsonb), 'PENDING')")
    void insert(@Param("id") String id, @Param("proposalId") String proposalId, @Param("projectId") String projectId,
                @Param("sortIndex") int sortIndex, @Param("entityType") String entityType, @Param("action") String action,
                @Param("targetId") String targetId, @Param("summary") String summary, @Param("payloadJson") String payloadJson,
                @Param("previousJson") String previousJson);

    @Modifying
    @Query("UPDATE workspace_change SET status = :status, feedback = :feedback, applied_at = CASE WHEN :status = 'APPLIED' THEN NOW() ELSE applied_at END, updated_at = NOW() WHERE id = :id AND proposal_id = :proposalId AND project_id = :projectId")
    int decide(@Param("id") String id, @Param("proposalId") String proposalId, @Param("projectId") String projectId,
               @Param("status") String status, @Param("feedback") String feedback);

    @Modifying
    @Query("DELETE FROM workspace_change WHERE proposal_id IN (SELECT id FROM workspace_change_proposal WHERE chat_session_id = :chatSessionId)")
    int deleteByChatSessionId(@Param("chatSessionId") String chatSessionId);
}
