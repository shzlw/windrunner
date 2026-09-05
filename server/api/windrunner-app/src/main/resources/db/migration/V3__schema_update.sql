CREATE TABLE proposal (
    id VARCHAR(64) PRIMARY KEY,
    workflow_type VARCHAR(40) NOT NULL,
    chat_session_id VARCHAR(64) NOT NULL,
    source_message_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by_actor_id VARCHAR(64),
    reviewed_at TIMESTAMPTZ,
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX proposal_session_idx
    ON proposal (chat_session_id, actor_id, created_at DESC, id);

CREATE TABLE proposal_change (
    id VARCHAR(64) PRIMARY KEY,
    proposal_id VARCHAR(64) NOT NULL,
    sort_index INTEGER NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    operation VARCHAR(20) NOT NULL,
    target_ref JSONB NOT NULL,
    payload JSONB NOT NULL,
    before_snapshot JSONB NOT NULL,
    after_snapshot JSONB NOT NULL,
    base_version JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    feedback TEXT,
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX proposal_change_order_idx
    ON proposal_change (proposal_id, sort_index, id);

CREATE INDEX proposal_change_entity_idx
    ON proposal_change (entity_type, operation, status);

ALTER TABLE team_member
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE project_member
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE project_team
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
