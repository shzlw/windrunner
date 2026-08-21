package com.windrunner.server.llm.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("llm_usage")
public class LlmUsage {

    @Id
    private String id;

    @Column("user_id")
    private String userId;

    @Column("project_id")
    private String projectId;

    private String feature;

    private String provider;

    private String model;

    @Column("input_tokens")
    private Long inputTokens;

    @Column("output_tokens")
    private Long outputTokens;

    @Column("total_tokens")
    private Long totalTokens;

    private String outcome;

    @Column("error_message")
    private String errorMessage;

    @Column("duration_ms")
    private Long durationMs;

    @Column("created_at")
    private OffsetDateTime createdAt;
}