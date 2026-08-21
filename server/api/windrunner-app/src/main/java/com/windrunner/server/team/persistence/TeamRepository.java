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
            SELECT id, name, created_at, updated_at
            FROM team
            ORDER BY lower(name) ASC, id ASC
            """)
    List<Team> findAllOrdered();

    @Query("""
            SELECT id, name, created_at, updated_at
            FROM team
            WHERE :query IS NULL
               OR :query = ''
               OR LOWER(name) LIKE CONCAT('%', LOWER(:query), '%')
            ORDER BY lower(name) ASC, id ASC
            LIMIT :limit
            """)
    List<Team> findAssignableTeams(@Param("query") String query, @Param("limit") int limit);

    @Query("""
            SELECT id, name, created_at, updated_at
            FROM team
            WHERE lower(name) = lower(:name)
            """)
    Optional<Team> findByNameIgnoreCase(@Param("name") String name);

    @Modifying
    @Query("""
            INSERT INTO team (
                id,
                name
            )
            VALUES (
                :id,
                :name
            )
            """)
    void insert(@Param("id") String id,
                @Param("name") String name);

    @Modifying
    @Query("""
            UPDATE team
            SET name = :name,
                updated_at = NOW()
            WHERE id = :id
            """)
    int update(@Param("id") String id,
               @Param("name") String name);
}
