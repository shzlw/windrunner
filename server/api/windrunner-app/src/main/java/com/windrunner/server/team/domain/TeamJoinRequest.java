package com.windrunner.server.team.domain;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("team_join_request")
public class TeamJoinRequest {

    @Id
    private String id;

    @Column("team_id")
    private String teamId;

    @Column("user_id")
    private String userId;

    private String status;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("decided_at")
    private OffsetDateTime decidedAt;

    @Column("decided_by_user_id")
    private String decidedByUserId;
}
