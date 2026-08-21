package com.windrunner.server.work.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("entry")
public class Entry {
    @Id
    private String id;
    @Column("project_id")
    private String projectId;
    @Column("work_item_id")
    private String workItemId;
    @Column("sort_index")
    private Integer sortIndex;
    @Column("author_user_id")
    private String authorUserId;
    @Transient
    private String authorDisplayName;
    private String type;
    private String body;
    @Column("created_at")
    private OffsetDateTime createdAt;
    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
