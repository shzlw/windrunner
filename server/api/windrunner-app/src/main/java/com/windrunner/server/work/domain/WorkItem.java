package com.windrunner.server.work.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Table("work_item")
public class WorkItem {
    @Id
    private String id;
    @Column("project_id")
    private String projectId;
    @Column("parent_work_item_id")
    private String parentWorkItemId;
    @Column("sort_index")
    private Integer sortIndex;
    private String type;
    private String title;
    private String status;
    @Column("due_date")
    private LocalDate dueDate;
    private String priority;
    @Column("created_by_user_id")
    private String createdByUserId;
    @Column("created_at")
    private OffsetDateTime createdAt;
    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
