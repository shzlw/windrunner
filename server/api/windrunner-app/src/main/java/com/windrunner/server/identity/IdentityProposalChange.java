package com.windrunner.server.identity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("proposal_change")
public class IdentityProposalChange {
    @Id private String id;
    private String proposalId;
    private Integer sortIndex;
    private String entityType;
    private String operation;
    private String targetRef;
    private String payload;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String baseVersion;
    private String status;
    private String feedback;
    private OffsetDateTime appliedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
