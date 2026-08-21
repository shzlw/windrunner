package com.windrunner.server.project.persistence;

import com.windrunner.server.project.domain.ProjectMember;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberRepository extends CrudRepository<ProjectMember, String> {

    @Query("""
            SELECT project_id, user_id, role, created_at
            FROM project_member
            WHERE project_id = :projectId
            ORDER BY role ASC, user_id ASC
            """)
    List<ProjectMember> findByProjectId(@Param("projectId") String projectId);

    @Query("""
            SELECT project_id, user_id, role, created_at
            FROM project_member
            WHERE project_id IN (:projectIds)
              AND role = 'OWNER'
            ORDER BY project_id ASC, user_id ASC
            """)
    List<ProjectMember> findOwnersByProjectIds(@Param("projectIds") List<String> projectIds);

    @Query("""
            SELECT project_id, user_id, role, created_at
            FROM project_member
            WHERE user_id = :userId
            ORDER BY project_id ASC
            """)
    List<ProjectMember> findByUserId(@Param("userId") String userId);

    @Query("""
            SELECT project_id, user_id, role, created_at
            FROM project_member
            WHERE project_id = :projectId
              AND user_id = :userId
            """)
    Optional<ProjectMember> findByProjectIdAndUserId(@Param("projectId") String projectId,
                                                     @Param("userId") String userId);

    @Query("""
            SELECT EXISTS(
                SELECT 1
                FROM project_member
                WHERE project_id = :projectId
                  AND user_id = :userId
                  AND role IN (:roles)
            )
            """)
    boolean hasDirectRole(@Param("projectId") String projectId,
                          @Param("userId") String userId,
                          @Param("roles") List<String> roles);

    @Query("""
            SELECT EXISTS(
                SELECT 1
                FROM project_team pt
                JOIN team_member tm ON tm.team_id = pt.team_id
                WHERE pt.project_id = :projectId
                  AND tm.user_id = :userId
                  AND pt.role IN (:roles)
            )
            """)
    boolean hasTeamRole(@Param("projectId") String projectId,
                        @Param("userId") String userId,
                        @Param("roles") List<String> roles);

    @Query("""
            SELECT COUNT(*)
            FROM project_member
            WHERE project_id = :projectId
              AND role = 'OWNER'
            """)
    int countOwners(@Param("projectId") String projectId);

    @Modifying
    @Query("""
            INSERT INTO project_member (
                project_id,
                user_id,
                role
            )
            VALUES (
                :projectId,
                :userId,
                :role
            )
            ON CONFLICT (project_id, user_id)
            DO UPDATE SET role = EXCLUDED.role
            """)
    void upsert(@Param("projectId") String projectId,
                @Param("userId") String userId,
                @Param("role") String role);

    @Modifying
    @Query("""
            DELETE FROM project_member
            WHERE project_id = :projectId
              AND user_id = :userId
            """)
    int delete(@Param("projectId") String projectId,
               @Param("userId") String userId);

    @Modifying
    @Query("""
            DELETE FROM project_member
            WHERE project_id = :projectId
            """)
    int deleteByProjectId(@Param("projectId") String projectId);

    @Modifying
    @Query("""
            DELETE FROM project_member
            WHERE user_id = :userId
            """)
    int deleteByUserId(@Param("userId") String userId);
}
