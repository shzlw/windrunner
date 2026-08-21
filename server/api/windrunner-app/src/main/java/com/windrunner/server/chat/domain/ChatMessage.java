package com.windrunner.server.chat.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("chat_message")
public class ChatMessage {

    @Id
    private String id;
    @Column("chat_session_id")
    private String chatSessionId;
    private String role;
    private String content;
    @Column("created_at")
    private OffsetDateTime createdAt;
}
