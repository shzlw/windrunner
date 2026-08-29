package com.windrunner.server.team.domain;

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
@Table("team")
public class Team {

    @Id
    private String id;
    private String name;
    private String description;
    @Column("created_at")
    private OffsetDateTime createdAt;
    @Column("updated_at")
    private OffsetDateTime updatedAt;
    @Transient
    private List<String> memberUserIds = new ArrayList<>();
    @Transient
    private Map<String, String> memberDisplayNames = new LinkedHashMap<>();
    @Transient
    private int projectCount;
    @Transient
    private String currentUserRole;
}
