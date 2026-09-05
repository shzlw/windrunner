package com.windrunner.server.team.persistence;

import com.windrunner.server.team.domain.TeamMember;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends CrudRepository<TeamMember, String> {

    @Query("""
            SELECT team_id, user_id, role, created_at, updated_at
            FROM team_member
            WHERE team_id = :teamId
            ORDER BY user_id ASC
            """)
    List<TeamMember> findByTeamId(@Param("teamId") String teamId);

    @Query("""
            SELECT team_id, user_id, role, created_at, updated_at
            FROM team_member
            WHERE team_id = :teamId
            ORDER BY user_id ASC
            LIMIT :limit OFFSET :offset
            """)
    List<TeamMember> findPageByTeamId(@Param("teamId") String teamId,
                                      @Param("limit") int limit,
                                      @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM team_member WHERE team_id = :teamId")
    long countByTeamId(@Param("teamId") String teamId);

    @Query("SELECT team_id, user_id, role, created_at, updated_at FROM team_member WHERE team_id = :teamId AND user_id = :userId")
    Optional<TeamMember> findByTeamIdAndUserId(@Param("teamId") String teamId,
                                               @Param("userId") String userId);

    @Query("SELECT COUNT(*) FROM team_member WHERE team_id = :teamId AND role = 'TEAM_OWNER'")
    long countOwners(@Param("teamId") String teamId);

    @Query("""
            SELECT team_id, user_id, role, created_at, updated_at
            FROM team_member
            WHERE team_id IN (:teamIds)
            ORDER BY team_id ASC, user_id ASC
            """)
    List<TeamMember> findByTeamIds(@Param("teamIds") List<String> teamIds);

    @Query("""
            SELECT team_id, user_id, role, created_at, updated_at
            FROM team_member
            WHERE user_id = :userId
            ORDER BY team_id ASC
            """)
    List<TeamMember> findByUserId(@Param("userId") String userId);

    @Query("""
            SELECT EXISTS(
                SELECT 1
                FROM team_member
                WHERE team_id = :teamId
                  AND user_id = :userId
                  AND role = 'TEAM_OWNER'
            )
            """)
    boolean isTeamOwner(@Param("teamId") String teamId,
                        @Param("userId") String userId);

    @Modifying
    @Query("""
            INSERT INTO team_member (
                team_id,
                user_id,
                role,
                updated_at
            )
            VALUES (
                :teamId,
                :userId,
                :role,
                NOW()
            )
            ON CONFLICT (team_id, user_id)
            DO UPDATE SET role = EXCLUDED.role, updated_at = NOW()
            """)
    void insert(@Param("teamId") String teamId,
                @Param("userId") String userId,
                @Param("role") String role);

    @Modifying
    @Query("INSERT INTO team_member (team_id, user_id, role, updated_at) VALUES (:teamId, :userId, :role, NOW()) ON CONFLICT (team_id, user_id) DO NOTHING")
    int insertIfAbsent(@Param("teamId") String teamId, @Param("userId") String userId, @Param("role") String role);

    @Modifying
    @Query("UPDATE team_member SET role = :role, updated_at = NOW() WHERE team_id = :teamId AND user_id = :userId AND updated_at = :expectedUpdatedAt")
    int updateRoleIfUnchanged(@Param("teamId") String teamId, @Param("userId") String userId,
                              @Param("role") String role, @Param("expectedUpdatedAt") java.time.OffsetDateTime expectedUpdatedAt);

    @Modifying
    @Query("DELETE FROM team_member WHERE team_id = :teamId AND user_id = :userId AND updated_at = :expectedUpdatedAt")
    int deleteIfUnchanged(@Param("teamId") String teamId, @Param("userId") String userId,
                          @Param("expectedUpdatedAt") java.time.OffsetDateTime expectedUpdatedAt);

    @Modifying
    @Query("DELETE FROM team_member WHERE team_id = :teamId AND user_id = :userId AND updated_at = :expectedUpdatedAt AND (role <> 'TEAM_OWNER' OR (SELECT COUNT(*) FROM team_member WHERE team_id = :teamId AND role = 'TEAM_OWNER') > 1)")
    int deleteIfUnchangedAndNotLastOwner(@Param("teamId") String teamId, @Param("userId") String userId,
                                         @Param("expectedUpdatedAt") java.time.OffsetDateTime expectedUpdatedAt);

    @Modifying
    @Query("""
            DELETE FROM team_member
            WHERE team_id = :teamId
              AND user_id = :userId
            """)
    int delete(@Param("teamId") String teamId,
               @Param("userId") String userId);

    @Modifying
    @Query("""
            DELETE FROM team_member
            WHERE team_id = :teamId
              AND user_id = :userId
              AND (
                    role <> 'TEAM_OWNER'
                    OR (SELECT COUNT(*) FROM team_member WHERE team_id = :teamId AND role = 'TEAM_OWNER') > 1
              )
            """)
    int deleteIfNotLastOwner(@Param("teamId") String teamId,
                             @Param("userId") String userId);

    @Modifying
    @Query("""
            DELETE FROM team_member
            WHERE team_id = :teamId
            """)
    int deleteByTeamId(@Param("teamId") String teamId);

    @Modifying
    @Query("""
            DELETE FROM team_member
            WHERE user_id = :userId
            """)
    int deleteByUserId(@Param("userId") String userId);
}
