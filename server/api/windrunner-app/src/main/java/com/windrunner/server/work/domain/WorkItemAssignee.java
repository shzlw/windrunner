package com.windrunner.server.work.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("work_item_assignee")
public class WorkItemAssignee {
    @Id
    private String id;
    @Column("work_item_id")
    private String workItemId;
    @Column("assignee_type")
    private String assigneeType;
    @Column("assignee_id")
    private String assigneeId;
    @Column("created_at")
    private OffsetDateTime createdAt;
}
