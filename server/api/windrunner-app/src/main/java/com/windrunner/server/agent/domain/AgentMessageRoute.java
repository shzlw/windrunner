package com.windrunner.server.agent.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("agent_message_route")
public class AgentMessageRoute {
    @Id
    private String id;
    @Column("user_id")
    private String userId;
    @Column("idempotency_key")
    private String idempotencyKey;
    @Column("ingestion_sequence")
    private long ingestionSequence;
    private String message;
    @Column("routed_chat_session_id")
    private String routedChatSessionId;
    @Column("routing_decision")
    private String routingDecision;
    private String status;
    @Column("context_ids")
    private String[] contextIds;
    @Column("lease_until")
    private OffsetDateTime leaseUntil;
    @Column("last_error")
    private String lastError;
    @Column("source_message_id")
    private String sourceMessageId;
    @Column("assistant_message_id")
    private String assistantMessageId;
    @Column("created_at")
    private OffsetDateTime createdAt;
    @Column("completed_at")
    private OffsetDateTime completedAt;
}
