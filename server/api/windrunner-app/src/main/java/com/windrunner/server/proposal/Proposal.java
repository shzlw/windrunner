package com.windrunner.server.proposal;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("proposal")
public class Proposal {
    @Id
    private String id;
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
}
