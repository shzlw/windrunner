package com.windrunner.server.team.persistence;

import com.windrunner.server.team.domain.ProjectTeam;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectTeamRepository extends CrudRepository<ProjectTeam, String> {

    record ProjectTeamWithName(
            String projectId,
            String teamId,
            String teamName,
            String role,
            OffsetDateTime createdAt
    ) {
    }

    @Query("""
            SELECT project_id, team_id, role, created_at, updated_at
            FROM project_team
            WHERE team_id = :teamId
            ORDER BY project_id ASC
            """)
    List<ProjectTeam> findByTeamId(@Param("teamId") String teamId);

    @Query("""
            SELECT project_id, team_id, role, created_at, updated_at
            FROM project_team
            WHERE team_id = :teamId
            ORDER BY project_id ASC
            LIMIT :limit OFFSET :offset
            """)
    List<ProjectTeam> findPageByTeamId(@Param("teamId") String teamId,
                                       @Param("limit") int limit,
                                       @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM project_team WHERE team_id = :teamId")
    long countByTeamId(@Param("teamId") String teamId);

    @Query("""
            SELECT project_id, team_id, role, created_at, updated_at
            FROM project_team
            WHERE team_id IN (:teamIds)
            ORDER BY team_id ASC, project_id ASC
            """)
    List<ProjectTeam> findByTeamIds(@Param("teamIds") List<String> teamIds);

    @Query("""
            SELECT project_id, team_id, role, created_at, updated_at
            FROM project_team
            WHERE project_id = :projectId
            ORDER BY team_id ASC
            """)
    List<ProjectTeam> findByProjectId(@Param("projectId") String projectId);

    @Query("""
            SELECT pt.project_id,
                   pt.team_id,
                   t.name AS team_name,
                   pt.role,
                   pt.created_at
            FROM project_team pt
            LEFT JOIN team t ON t.id = pt.team_id
            WHERE pt.project_id = :projectId
            ORDER BY pt.team_id ASC
            """)
    List<ProjectTeamWithName> findByProjectIdWithTeamName(@Param("projectId") String projectId);

    @Query("""
            SELECT project_id, team_id, role, created_at, updated_at
            FROM project_team
            WHERE project_id = :projectId
            ORDER BY team_id ASC
            LIMIT :limit OFFSET :offset
            """)
    List<ProjectTeam> findPageByProjectId(@Param("projectId") String projectId,
                                          @Param("limit") int limit,
                                          @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM project_team WHERE project_id = :projectId")
    long countByProjectId(@Param("projectId") String projectId);

    @Query("""
            SELECT project_id, team_id, role, created_at, updated_at
            FROM project_team
            WHERE project_id = :projectId
              AND team_id = :teamId
            """)
    Optional<ProjectTeam> findByProjectIdAndTeamId(@Param("projectId") String projectId,
                                                   @Param("teamId") String teamId);

    @Query("""
            SELECT COUNT(*)
            FROM project_team
            WHERE project_id = :projectId
              AND role = 'OWNER'
            """)
    int countOwners(@Param("projectId") String projectId);

    @Modifying
    @Query("""
            INSERT INTO project_team (
                project_id,
                team_id,
                role
            )
            VALUES (
                :projectId,
                :teamId,
                :role
            )
            ON CONFLICT (project_id, team_id)
            DO UPDATE SET role = EXCLUDED.role, updated_at = NOW()
            """)
    void upsert(@Param("projectId") String projectId,
                @Param("teamId") String teamId,
                @Param("role") String role);

    @Modifying
    @Query("INSERT INTO project_team (project_id, team_id, role, updated_at) VALUES (:projectId, :teamId, :role, NOW()) ON CONFLICT (project_id, team_id) DO NOTHING")
    int insertIfAbsent(@Param("projectId") String projectId, @Param("teamId") String teamId, @Param("role") String role);

    @Modifying
    @Query("UPDATE project_team SET role = :role, updated_at = NOW() WHERE project_id = :projectId AND team_id = :teamId AND updated_at = :expectedUpdatedAt")
    int updateRoleIfUnchanged(@Param("projectId") String projectId, @Param("teamId") String teamId,
                              @Param("role") String role, @Param("expectedUpdatedAt") OffsetDateTime expectedUpdatedAt);

    @Modifying
    @Query("DELETE FROM project_team WHERE project_id = :projectId AND team_id = :teamId AND updated_at = :expectedUpdatedAt")
    int deleteIfUnchanged(@Param("projectId") String projectId, @Param("teamId") String teamId,
                          @Param("expectedUpdatedAt") OffsetDateTime expectedUpdatedAt);

    @Modifying
    @Query("""
            DELETE FROM project_team
            WHERE project_id = :projectId
              AND team_id = :teamId
            """)
    int delete(@Param("projectId") String projectId,
               @Param("teamId") String teamId);

    @Modifying
    @Query("""
            DELETE FROM project_team
            WHERE team_id = :teamId
            """)
    int deleteByTeamId(@Param("teamId") String teamId);

    @Modifying
    @Query("""
            DELETE FROM project_team
            WHERE project_id = :projectId
            """)
    int deleteByProjectId(@Param("projectId") String projectId);
}
