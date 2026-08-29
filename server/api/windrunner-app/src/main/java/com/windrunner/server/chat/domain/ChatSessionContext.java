package com.windrunner.server.chat.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("chat_session_context")
public class ChatSessionContext {
    @Id
    private String id;
    @Column("chat_session_id")
    private String chatSessionId;
    @Column("entity_type")
    private String entityType;
    @Column("entity_id")
    private String entityId;
    @Column("created_at")
    private OffsetDateTime createdAt;
}
