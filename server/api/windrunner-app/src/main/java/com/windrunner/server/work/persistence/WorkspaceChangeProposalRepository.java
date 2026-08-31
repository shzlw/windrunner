package com.windrunner.server.work.persistence;

import com.windrunner.server.work.domain.WorkspaceChangeProposal;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceChangeProposalRepository extends CrudRepository<WorkspaceChangeProposal, String> {
    String COLUMNS = "id, project_id, chat_session_id, source_message_id, source_text, status, created_at, updated_at";

    @Query("SELECT " + COLUMNS + " FROM workspace_change_proposal WHERE project_id = :projectId ORDER BY created_at DESC, id DESC")
    List<WorkspaceChangeProposal> findByProjectId(@Param("projectId") String projectId);

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
