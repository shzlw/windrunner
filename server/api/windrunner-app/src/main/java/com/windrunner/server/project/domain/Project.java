package com.windrunner.server.project.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Table("project")
public class Project {

    @Id
    private String id;
    private String name;
    @Column("created_by_user_id")
    private String createdByUserId;
    @Column("created_at")
    private OffsetDateTime createdAt;
    @Column("updated_at")
    private OffsetDateTime updatedAt;
    @Column("archived_at")
    private OffsetDateTime archivedAt;
    @Transient
    private List<String> ownerUserIds = new ArrayList<>();
    @Transient
    private Map<String, String> ownerDisplayNames = new LinkedHashMap<>();
}
