package com.windrunner.server.identity;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface IdentityProposalRepository extends CrudRepository<IdentityProposal, String> {
    String COLUMNS = "id, workflow_type, chat_session_id, source_message_id, actor_id, status, reviewed_by_actor_id, reviewed_at, applied_at, created_at, updated_at";

    @Query("SELECT " + COLUMNS + " FROM proposal WHERE workflow_type = 'IDENTITY' AND chat_session_id = :sessionId AND actor_id = :actorId ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
    List<IdentityProposal> page(@Param("sessionId") String sessionId, @Param("actorId") String actorId,
                                @Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT " + COLUMNS + " FROM proposal WHERE workflow_type = 'IDENTITY' AND id = :id AND chat_session_id = :sessionId AND actor_id = :actorId")
    Optional<IdentityProposal> findForDecision(@Param("id") String id, @Param("sessionId") String sessionId,
                                               @Param("actorId") String actorId);

    @Modifying
    @Query("UPDATE proposal SET status = 'APPLYING', updated_at = NOW() WHERE id = :id AND chat_session_id = :sessionId AND actor_id = :actorId AND status = 'PENDING'")
    int claimForDecision(@Param("id") String id, @Param("sessionId") String sessionId, @Param("actorId") String actorId);

    /**
     * The legacy argument names are retained at this boundary so existing callers keep compiling.
     * The generic parent stores workflow metadata; entity-specific data belongs to proposal_change.
     */
    @Modifying
    @Query("INSERT INTO proposal (id, workflow_type, chat_session_id, source_message_id, actor_id, status) VALUES (:id, 'IDENTITY', :sessionId, :messageId, :actorId, 'PENDING')")
    void insert(@Param("id") String id, @Param("sessionId") String sessionId, @Param("messageId") String messageId,
                @Param("actorId") String actorId, @Param("kind") String kind, @Param("draft") String draft,
                @Param("before") String before, @Param("after") String after);

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
