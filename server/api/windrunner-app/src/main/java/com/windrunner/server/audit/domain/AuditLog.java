package com.windrunner.server.audit.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("audit_log")
public class AuditLog {

    @Id
    private String id;

    @Column("occurred_at")
    private OffsetDateTime occurredAt;

    @Column("actor_user_id")
    private String actorUserId;

    private String action;

    @Column("entity_type")
    private String entityType;

    @Column("entity_id")
    private String entityId;

    @Column("project_id")
    private String projectId;

    private String outcome;

    private String summary;

    @Column("before_json")
    private String beforeJson;

    @Column("after_json")
    private String afterJson;

    @Column("changes_json")
    private String changesJson;

    @Column("metadata_json")
    private String metadataJson;

    @Transient
    private String actorDisplayName;

    @Transient
    private String entityDisplayName;

    @Transient
    private String projectName;
}
