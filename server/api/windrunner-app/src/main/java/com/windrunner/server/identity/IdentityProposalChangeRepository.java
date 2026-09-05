package com.windrunner.server.identity;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IdentityProposalChangeRepository extends CrudRepository<IdentityProposalChange, String> {
    String COLUMNS = "id, proposal_id, sort_index, entity_type, operation, target_ref::text AS target_ref, payload::text AS payload, before_snapshot::text AS before_snapshot, after_snapshot::text AS after_snapshot, base_version::text AS base_version, status, feedback, applied_at, created_at, updated_at";

    @Query("SELECT " + COLUMNS + " FROM proposal_change WHERE proposal_id = :proposalId ORDER BY sort_index, id")
    List<IdentityProposalChange> findByProposalId(@Param("proposalId") String proposalId);

    @Modifying
    @Query("INSERT INTO proposal_change (id, proposal_id, sort_index, entity_type, operation, target_ref, payload, before_snapshot, after_snapshot, base_version, status) VALUES (:id, :proposalId, :sortIndex, :entityType, :operation, CAST(:targetRef AS jsonb), CAST(:payload AS jsonb), CAST(:beforeSnapshot AS jsonb), CAST(:afterSnapshot AS jsonb), CAST(:baseVersion AS jsonb), 'PENDING')")
    void insert(@Param("id") String id, @Param("proposalId") String proposalId, @Param("sortIndex") int sortIndex,
                @Param("entityType") String entityType, @Param("operation") String operation,
                @Param("targetRef") String targetRef, @Param("payload") String payload,
                @Param("beforeSnapshot") String beforeSnapshot, @Param("afterSnapshot") String afterSnapshot,
                @Param("baseVersion") String baseVersion);

    @Modifying
    @Query("UPDATE proposal_change SET status = :status, feedback = :feedback, applied_at = CASE WHEN :status = 'APPLIED' THEN NOW() ELSE applied_at END, updated_at = NOW() WHERE id = :id AND proposal_id = :proposalId AND status = 'PENDING'")
    int decide(@Param("id") String id, @Param("proposalId") String proposalId, @Param("status") String status,
               @Param("feedback") String feedback);
}
