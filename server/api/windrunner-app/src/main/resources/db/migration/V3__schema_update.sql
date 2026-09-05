CREATE TABLE agent_message_route
(
    id                     TEXT PRIMARY KEY,
    user_id                TEXT NOT NULL,
    idempotency_key        TEXT NOT NULL,
    ingestion_sequence     BIGINT GENERATED ALWAYS AS IDENTITY,
    message                TEXT NOT NULL,
    routed_chat_session_id TEXT,
    routing_decision       TEXT,
    status                 TEXT NOT NULL DEFAULT 'RECEIVED',
    source_message_id      TEXT,
    assistant_message_id   TEXT,
    context_ids            TEXT[] NOT NULL DEFAULT '{}',
    lease_until            TIMESTAMPTZ,
    last_error             TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at           TIMESTAMPTZ,

    UNIQUE (user_id, idempotency_key)
);

CREATE INDEX agent_message_route_user_sequence_idx
    ON agent_message_route (user_id, ingestion_sequence DESC);

CREATE INDEX agent_message_route_session_sequence_idx
    ON agent_message_route (routed_chat_session_id, ingestion_sequence);

CREATE UNIQUE INDEX agent_message_route_source_message_idx
    ON agent_message_route (source_message_id)
    WHERE source_message_id IS NOT NULL;

CREATE UNIQUE INDEX agent_message_route_assistant_message_idx
    ON agent_message_route (assistant_message_id)
    WHERE assistant_message_id IS NOT NULL;

CREATE INDEX agent_message_route_pending_routing_idx
    ON agent_message_route (user_id, ingestion_sequence)
    WHERE status IN ('RECEIVED', 'ROUTING');

CREATE INDEX agent_message_route_pending_processing_idx
    ON agent_message_route (routed_chat_session_id, ingestion_sequence)
    WHERE status IN ('ROUTED', 'PROCESSING');

CREATE UNIQUE INDEX agent_message_route_one_router_idx
    ON agent_message_route (user_id) WHERE status = 'ROUTING';

CREATE UNIQUE INDEX agent_message_route_one_processor_idx
    ON agent_message_route (routed_chat_session_id) WHERE status = 'PROCESSING';
