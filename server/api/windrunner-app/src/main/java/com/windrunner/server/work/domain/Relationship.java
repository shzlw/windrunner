package com.windrunner.server.work.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("relationship")
public class Relationship {
    @Id
    private String id;
    @Column("project_id")
    private String projectId;
    @Column("from_entity_type")
    private String fromEntityType;
    @Column("from_entity_id")
    private String fromEntityId;
    @Column("to_entity_type")
    private String toEntityType;
    @Column("to_entity_id")
    private String toEntityId;
    private String type;
    private String reason;
    @Column("source_entry_id")
    private String sourceEntryId;
    @Column("created_by_user_id")
    private String createdByUserId;
    @Column("created_at")
    private OffsetDateTime createdAt;
}
