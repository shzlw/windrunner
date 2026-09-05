package com.windrunner.server.team.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("project_team")
public class ProjectTeam {

    @Id
    @Column("project_id")
    private String projectId;
    @Column("team_id")
    private String teamId;
    private String role;
    @Column("created_at")
    private OffsetDateTime createdAt;
    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
