package com.windrunner.server.chat.domain;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("chat_session")
public class ChatSession {

    @Id
    private String id;
    @Column("project_id")
    private String projectId;
    @Column("user_id")
    private String userId;
    private String status;
    @Column("created_at")
    private OffsetDateTime createdAt;
    @Column("updated_at")
    private OffsetDateTime updatedAt;
    @Column("archived_at")
    private OffsetDateTime archivedAt;
}
