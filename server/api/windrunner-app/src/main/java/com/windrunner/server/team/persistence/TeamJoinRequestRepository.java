package com.windrunner.server.team.persistence;

import com.windrunner.server.team.domain.TeamJoinRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamJoinRequestRepository extends CrudRepository<TeamJoinRequest, String> {

    @Query("""
            SELECT id, team_id, user_id, status, created_at, decided_at, decided_by_user_id
            FROM team_join_request
            WHERE team_id = :teamId
              AND status = 'PENDING'
            ORDER BY created_at ASC, id ASC
            """)
    List<TeamJoinRequest> findPendingByTeamId(@Param("teamId") String teamId);

    @Query("""
            SELECT id, team_id, user_id, status, created_at, decided_at, decided_by_user_id
            FROM team_join_request
            WHERE user_id = :userId
            ORDER BY created_at DESC, id DESC
            """)
    List<TeamJoinRequest> findByUserId(@Param("userId") String userId);

    @Override
    @Query("""
            SELECT id, team_id, user_id, status, created_at, decided_at, decided_by_user_id
            FROM team_join_request
            WHERE id = :id
            """)
    Optional<TeamJoinRequest> findById(@Param("id") String id);

    @Query("""
            SELECT EXISTS(
                SELECT 1
                FROM team_join_request
                WHERE team_id = :teamId
                  AND user_id = :userId
                  AND status = 'PENDING'
            )
            """)
    boolean hasPendingRequest(@Param("teamId") String teamId,
                              @Param("userId") String userId);

    @Modifying
    @Query("""
            INSERT INTO team_join_request (
                id,
                team_id,
                user_id,
                status
            )
            VALUES (
                :id,
                :teamId,
                :userId,
                'PENDING'
            )
            """)
    void insertPending(@Param("id") String id,
                       @Param("teamId") String teamId,
                       @Param("userId") String userId);

    @Modifying
    @Query("""
            UPDATE team_join_request
            SET status = :status,
                decided_at = NOW(),
                decided_by_user_id = :decidedByUserId
            WHERE id = :id
              AND status = 'PENDING'
            """)
    int decide(@Param("id") String id,
               @Param("status") String status,
               @Param("decidedByUserId") String decidedByUserId);
}
