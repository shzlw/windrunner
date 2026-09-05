package com.windrunner.server.team.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("team_member")
public class TeamMember {

    @Id
    @Column("team_id")
    private String teamId;
    @Column("user_id")
    private String userId;
    private String role;
    @Column("created_at")
    private OffsetDateTime createdAt;
    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
