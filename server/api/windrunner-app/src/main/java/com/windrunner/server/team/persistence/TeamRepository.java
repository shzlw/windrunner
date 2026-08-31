package com.windrunner.server.team.persistence;

import com.windrunner.server.team.domain.Team;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends CrudRepository<Team, String> {

    @Query("""
            SELECT id, name, description, created_at, updated_at
            FROM team
            ORDER BY lower(name) ASC, id ASC
            """)
    List<Team> findAllOrdered();

    @Query("""
            SELECT id, name, description, created_at, updated_at
            FROM team
            ORDER BY lower(name) ASC, id ASC
            LIMIT :limit OFFSET :offset
            """)
    List<Team> findAllPage(@Param("limit") int limit, @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM team")
    long countTeams();

    @Query("""
            SELECT id, name, description, created_at, updated_at
            FROM team
            WHERE :query IS NULL
               OR :query = ''
               OR LOWER(name) LIKE CONCAT('%', LOWER(:query), '%')
               OR LOWER(COALESCE(description, '')) LIKE CONCAT('%', LOWER(:query), '%')
            ORDER BY lower(name) ASC, id ASC
            LIMIT :limit
            """)
    List<Team> findAssignableTeams(@Param("query") String query, @Param("limit") int limit);

    @Query("""
            SELECT t.id, t.name, t.description, t.created_at, t.updated_at
            FROM team t
            WHERE EXISTS (
                    SELECT 1
                    FROM project_team pt
                    WHERE pt.project_id = :projectId
                      AND pt.team_id = t.id
              )
              AND (
                    :query IS NULL
                 OR :query = ''
                 OR LOWER(t.name) LIKE CONCAT('%', LOWER(:query), '%')
                 OR LOWER(COALESCE(t.description, '')) LIKE CONCAT('%', LOWER(:query), '%')
              )
            ORDER BY lower(t.name) ASC, t.id ASC
            LIMIT :limit
            """)
    List<Team> findAssignableTeamsForProject(@Param("projectId") String projectId,
                                             @Param("query") String query,
                                             @Param("limit") int limit);

    @Query("""
            SELECT id, name, description, created_at, updated_at
            FROM team
            WHERE lower(name) = lower(:name)
            """)
    Optional<Team> findByNameIgnoreCase(@Param("name") String name);

    @Modifying
    @Query("""
            INSERT INTO team (
                id,
                name,
                description
            )
            VALUES (
                :id,
                :name,
                :description
            )
            """)
    void insert(@Param("id") String id,
                @Param("name") String name,
                @Param("description") String description);

    @Modifying
    @Query("""
            UPDATE team
            SET name = :name,
                description = :description,
                updated_at = NOW()
            WHERE id = :id
            """)
    int update(@Param("id") String id,
               @Param("name") String name,
               @Param("description") String description);
}
