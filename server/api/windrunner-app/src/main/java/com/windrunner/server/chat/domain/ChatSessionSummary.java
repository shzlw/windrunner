package com.windrunner.server.chat.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("chat_session_summary")
public class ChatSessionSummary {

    @Id
    private String id;
    @Column("chat_session_id")
    private String chatSessionId;
    private String summary;
    @Column("summarized_through_message_id")
    private String summarizedThroughMessageId;
    @Column("summarized_through_created_at")
    private OffsetDateTime summarizedThroughCreatedAt;
    @Column("created_at")
    private OffsetDateTime createdAt;
    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
