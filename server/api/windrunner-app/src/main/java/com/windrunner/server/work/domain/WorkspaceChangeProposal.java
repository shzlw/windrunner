package com.windrunner.server.work.domain;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("workspace_change_proposal")
public class WorkspaceChangeProposal {
    @Id private String id;
    @Column("project_id") private String projectId;
    @Column("chat_session_id") private String chatSessionId;
    @Column("source_message_id") private String sourceMessageId;
    @Column("source_text") private String sourceText;
    private String status;
    @Column("created_at") private OffsetDateTime createdAt;
    @Column("updated_at") private OffsetDateTime updatedAt;
}
