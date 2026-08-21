package com.windrunner.server.subscription.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("work_item_subscription")
public class WorkItemSubscription {

    @Id
    private String id;

    @Column("user_id")
    private String userId;

    @Column("project_id")
    private String projectId;

    @Column("work_item_id")
    private String workItemId;

    @Column("created_at")
    private OffsetDateTime createdAt;
}