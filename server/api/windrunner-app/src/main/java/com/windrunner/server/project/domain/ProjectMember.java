package com.windrunner.server.project.domain;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("project_member")
public class ProjectMember {

    @Id
    @Column("project_id")
    private String projectId;

    @Column("user_id")
    private String userId;

    private String role;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
