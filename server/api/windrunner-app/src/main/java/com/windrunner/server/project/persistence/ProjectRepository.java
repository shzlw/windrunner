package com.windrunner.server.project.persistence;

import com.windrunner.server.project.domain.Project;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends CrudRepository<Project, String> {

    @Query("SELECT id, name, created_by_user_id, created_at, updated_at, archived_at FROM project ORDER BY name ASC, id ASC")
    List<Project> findAllByOrderByNameAscIdAsc();

    @Query("""
            SELECT DISTINCT p.id, p.name, p.created_by_user_id, p.created_at, p.updated_at, p.archived_at
            FROM project p
            WHERE EXISTS (
                SELECT 1
                FROM project_member pm
                WHERE pm.project_id = p.id
                  AND pm.user_id = :userId
            )
               OR EXISTS (
                SELECT 1
                FROM project_team pt
                JOIN team_member tm ON tm.team_id = pt.team_id
                WHERE pt.project_id = p.id
                  AND tm.user_id = :userId
            )
            ORDER BY p.name ASC, p.id ASC
            """)
    List<Project> findVisibleToUser(@Param("userId") String userId);

    @Override
    @Query("""
            SELECT id, name, created_by_user_id, created_at, updated_at, archived_at
            FROM project
            WHERE id = :id
            """)
    java.util.Optional<Project> findById(@Param("id") String id);

    @Modifying
    @Query("""
            INSERT INTO project (
                id,
                name,
                created_by_user_id
            )
            VALUES (
                :id,
                :name,
                :createdByUserId
            )
            """)
    void insert(@Param("id") String id,
                @Param("name") String name,
                @Param("createdByUserId") String createdByUserId);

    @Modifying
    @Query("""
            UPDATE project
            SET name = :name,
                updated_at = NOW()
            WHERE id = :id
            """)
    int update(@Param("id") String id, @Param("name") String name);
}
