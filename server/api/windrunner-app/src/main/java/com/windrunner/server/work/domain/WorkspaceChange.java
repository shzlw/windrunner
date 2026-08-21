package com.windrunner.server.work.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("workspace_change")
public class WorkspaceChange {
    @Id
    private String id;
    @Column("proposal_id")
    private String proposalId;
    @Column("project_id")
    private String projectId;
    @Column("sort_index")
    private Integer sortIndex;
    @Column("entity_type")
    private String entityType;
    private String action;
    @Column("target_id")
    private String targetId;
    private String summary;
    @JsonIgnore
    @Column("payload_json")
    private String payloadJson;
    @JsonIgnore
    @Column("previous_json")
    private String previousJson;
    private String status;
    private String feedback;
    @Column("applied_at")
    private OffsetDateTime appliedAt;
    @Column("created_at")
    private OffsetDateTime createdAt;
    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
