package com.windrunner.server.user.persistence;

import com.windrunner.server.user.domain.AppUser;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends CrudRepository<AppUser, String> {

    @Modifying
    @Query("""
            INSERT INTO app_user (
                id,
                username,
                email,
                display_name,
                title,
                bio,
                timezone,
                password_hash,
                status,
                global_role,
                must_change_password,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :username,
                :email,
                :displayName,
                :title,
                :bio,
                :timezone,
                :passwordHash,
                :status,
                :globalRole,
                :mustChangePassword,
                :createdAt,
                :updatedAt
            )
            """)
    int insertUser(@Param("id") String id,
                   @Param("username") String username,
                   @Param("email") String email,
                   @Param("displayName") String displayName,
                   @Param("title") String title,
                   @Param("bio") String bio,
                   @Param("timezone") String timezone,
                   @Param("passwordHash") String passwordHash,
                   @Param("status") String status,
                   @Param("globalRole") String globalRole,
                   @Param("mustChangePassword") boolean mustChangePassword,
                   @Param("createdAt") java.time.OffsetDateTime createdAt,
                   @Param("updatedAt") java.time.OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            UPDATE app_user
            SET username = :username,
                email = :email,
                display_name = :displayName,
                title = :title,
                bio = :bio,
                timezone = :timezone,
                status = :status,
                global_role = :globalRole,
                updated_at = :updatedAt
            WHERE id = :id
            """)
    int updateUserProfile(@Param("id") String id,
                          @Param("username") String username,
                          @Param("email") String email,
                          @Param("displayName") String displayName,
                          @Param("title") String title,
                          @Param("bio") String bio,
                          @Param("timezone") String timezone,
                          @Param("status") String status,
                          @Param("globalRole") String globalRole,
                          @Param("updatedAt") java.time.OffsetDateTime updatedAt);

    @Modifying
    @Query("""
            UPDATE app_user
            SET password_hash = :passwordHash,
                must_change_password = :mustChangePassword,
                updated_at = :updatedAt
            WHERE id = :id
            """)
    int updateUserPassword(@Param("id") String id,
                           @Param("passwordHash") String passwordHash,
                           @Param("mustChangePassword") boolean mustChangePassword,
                           @Param("updatedAt") java.time.OffsetDateTime updatedAt);

    @Query("""
            SELECT *
            FROM app_user
            WHERE LOWER(username) = LOWER(:username)
            LIMIT 1
            """)
    Optional<AppUser> findByUsername(@Param("username") String username);

    @Query("""
            SELECT *
            FROM app_user
            WHERE email IS NOT NULL
              AND LOWER(email) = LOWER(:email)
            LIMIT 1
            """)
    Optional<AppUser> findByEmail(@Param("email") String email);

    @Query("""
            SELECT *
            FROM app_user
            WHERE LOWER(username) = LOWER(:login)
               OR (email IS NOT NULL AND LOWER(email) = LOWER(:login))
            LIMIT 1
            """)
    Optional<AppUser> findByUsernameOrEmail(@Param("login") String login);

    @Query("""
            SELECT *
            FROM app_user
            WHERE UPPER(global_role) = 'USER'
            ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<AppUser> findUserPage(@Param("limit") int limit, @Param("offset") long offset);

    @Query("""
            SELECT *
            FROM app_user
            WHERE UPPER(global_role) IN ('USER', 'ADMIN')
            ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<AppUser> findUserAndAdminPage(@Param("limit") int limit, @Param("offset") long offset);

    @Query("""
            SELECT id, username, email, display_name, title, bio
            FROM app_user
            WHERE UPPER(status) = 'ACTIVE'
              AND (
                    :query IS NULL
                 OR :query = ''
                 OR LOWER(username) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(COALESCE(display_name, '')) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(COALESCE(email, '')) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(COALESCE(title, '')) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(COALESCE(bio, '')) LIKE CONCAT('%', LOWER(:query), '%')
              )
            ORDER BY lower(COALESCE(display_name, username)) ASC, id ASC
            LIMIT :limit
            """)
    List<AppUser> findActiveAssignableUsers(@Param("query") String query, @Param("limit") int limit);

    @Query("""
            SELECT u.id, u.username, u.email, u.display_name, u.title, u.bio
            FROM app_user u
            WHERE UPPER(u.status) = 'ACTIVE'
              AND (
                    EXISTS (
                        SELECT 1
                        FROM project_member pm
                        WHERE pm.project_id = :projectId
                          AND pm.user_id = u.id
                    )
                 OR EXISTS (
                        SELECT 1
                        FROM project_team pt
                        JOIN team_member tm ON tm.team_id = pt.team_id
                        WHERE pt.project_id = :projectId
                          AND tm.user_id = u.id
                          AND pt.role IN ('OWNER', 'EDITOR', 'VIEWER')
                    )
              )
              AND (
                    :query IS NULL
                 OR :query = ''
                 OR LOWER(u.username) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(COALESCE(u.display_name, '')) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(COALESCE(u.email, '')) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(COALESCE(u.title, '')) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(COALESCE(u.bio, '')) LIKE CONCAT('%', LOWER(:query), '%')
              )
            ORDER BY lower(COALESCE(u.display_name, u.username)) ASC, u.id ASC
            LIMIT :limit
            """)
    List<AppUser> findAssignableUsersForProject(@Param("projectId") String projectId,
                                                @Param("query") String query,
                                                @Param("limit") int limit);

    @Query("""
            SELECT id, username, email, display_name, title, bio
            FROM app_user
            WHERE UPPER(status) = 'ACTIVE'
              AND id IN (:userIds)
            """)
    List<AppUser> findActiveUsersByIds(@Param("userIds") List<String> userIds);

    @Query("""
            SELECT COUNT(*)
            FROM app_user
            """)
    long countUsers();

    @Query("""
            SELECT COUNT(*)
            FROM app_user
            WHERE UPPER(global_role) = 'USER'
            """)
    long countUsersWithUserRole();

    @Query("""
            SELECT COUNT(*)
            FROM app_user
            WHERE UPPER(global_role) IN ('USER', 'ADMIN')
            """)
    long countUsersWithUserOrAdminRole();

    @Modifying
    @Query("""
            UPDATE app_user
            SET status = :status,
                updated_at = :updatedAt
            WHERE id = :id
            """)
    int updateUserStatus(@Param("id") String id,
                         @Param("status") String status,
                         @Param("updatedAt") java.time.OffsetDateTime updatedAt);
}
