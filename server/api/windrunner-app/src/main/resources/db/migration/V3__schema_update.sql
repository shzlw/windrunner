CREATE TABLE proposal
(
    id                   VARCHAR(64) PRIMARY KEY,
    workflow_type        VARCHAR(40) NOT NULL,
    chat_session_id      VARCHAR(64) NOT NULL,
    source_message_id    VARCHAR(64) NOT NULL,
    actor_id             VARCHAR(64) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by_actor_id VARCHAR(64),
    reviewed_at          TIMESTAMPTZ,
    applied_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX proposal_session_idx
    ON proposal (chat_session_id, actor_id, created_at DESC, id);

CREATE TABLE proposal_change
(
    id              VARCHAR(64) PRIMARY KEY,
    proposal_id     VARCHAR(64) NOT NULL,
    sort_index      INTEGER     NOT NULL,
    entity_type     VARCHAR(40) NOT NULL,
    operation       VARCHAR(20) NOT NULL,
    target_ref      JSONB       NOT NULL,
    payload         JSONB       NOT NULL,
    before_snapshot JSONB       NOT NULL,
    after_snapshot  JSONB       NOT NULL,
    base_version    JSONB,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    feedback        TEXT,
    applied_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
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

CREATE TABLE chat_session_summary
(
    id                            TEXT PRIMARY KEY,
    chat_session_id               TEXT        NOT NULL,
    summary                       TEXT        NOT NULL,
    summarized_through_message_id TEXT        NOT NULL,
    summarized_through_created_at TIMESTAMPTZ NOT NULL,
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX chat_session_summary_session_idx
    ON chat_session_summary (chat_session_id);

CREATE TABLE calendar_event
(
    id                 TEXT PRIMARY KEY,
    user_id            TEXT         NOT NULL,
    event_type         VARCHAR(40)  NOT NULL,
    title              VARCHAR(200) NOT NULL,
    description        TEXT,
    starts_at          TIMESTAMPTZ  NOT NULL,
    ends_at            TIMESTAMPTZ  NOT NULL,
    timezone           VARCHAR(100) NOT NULL,
    all_day            BOOLEAN      NOT NULL DEFAULT FALSE,
    show_as_busy       BOOLEAN      NOT NULL DEFAULT TRUE,
    work_item_id       TEXT,
    created_by_user_id TEXT         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK (ends_at > starts_at)
);

CREATE INDEX calendar_event_user_time_idx
    ON calendar_event (user_id, starts_at, ends_at, id);

CREATE INDEX calendar_event_work_item_idx
    ON calendar_event (work_item_id)
    WHERE work_item_id IS NOT NULL;
