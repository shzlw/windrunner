package com.windrunner.server.identity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.OffsetDateTime;

@Data
@Table("proposal")
public class IdentityProposal {
    @Id private String id;
    private String workflowType;
    private String chatSessionId;
    private String sourceMessageId;
    private String actorId;
    private String status;
    private String reviewedByActorId;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime appliedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** Compatibility accessors kept for callers/tests that construct the old one-change shape. */
    @org.springframework.data.annotation.Transient private String kind;
    @org.springframework.data.annotation.Transient private String draftJson;
    @org.springframework.data.annotation.Transient private String beforeJson;
    @org.springframework.data.annotation.Transient private String afterJson;
}
