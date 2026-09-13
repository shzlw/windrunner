CREATE TABLE proposal
(
    id                   VARCHAR(64) PRIMARY KEY,
    workflow_type        VARCHAR(40) NOT NULL,
    project_id           VARCHAR(64),
    source_type          VARCHAR(20) NOT NULL DEFAULT 'CHAT',
    chat_session_id      VARCHAR(64),
    source_message_id    VARCHAR(64),
    source_text          TEXT,
    source_api_key_id    VARCHAR(64),
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

CREATE INDEX proposal_project_idx
    ON proposal (project_id, created_at DESC, id)
    WHERE project_id IS NOT NULL;

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

INSERT INTO proposal (
    id,
    workflow_type,
    project_id,
    source_type,
    chat_session_id,
    source_message_id,
    source_text,
    actor_id,
    status,
    created_at,
    updated_at
)
SELECT
    workspace_proposal.id,
    'WORKSPACE',
    workspace_proposal.project_id,
    'CHAT',
    workspace_proposal.chat_session_id,
    workspace_proposal.source_message_id,
    workspace_proposal.source_text,
    chat_session.user_id,
    CASE
        WHEN EXISTS (
            SELECT 1
            FROM workspace_change open_change
            WHERE open_change.proposal_id = workspace_proposal.id
              AND open_change.status IN ('PENDING', 'NEEDS_UPDATE')
        ) THEN 'PENDING'
        WHEN NOT EXISTS (
            SELECT 1
            FROM workspace_change non_applied_change
            WHERE non_applied_change.proposal_id = workspace_proposal.id
              AND non_applied_change.status <> 'APPLIED'
        ) THEN 'APPLIED'
        WHEN NOT EXISTS (
            SELECT 1
            FROM workspace_change non_rejected_change
            WHERE non_rejected_change.proposal_id = workspace_proposal.id
              AND non_rejected_change.status <> 'REJECTED'
        ) THEN 'REJECTED'
        ELSE 'COMPLETED'
    END,
    workspace_proposal.created_at,
    workspace_proposal.updated_at
FROM workspace_change_proposal workspace_proposal
JOIN chat_session ON chat_session.id = workspace_proposal.chat_session_id;

INSERT INTO proposal_change (
    id,
    proposal_id,
    sort_index,
    entity_type,
    operation,
    target_ref,
    payload,
    before_snapshot,
    after_snapshot,
    base_version,
    status,
    feedback,
    applied_at,
    created_at,
    updated_at
)
SELECT
    workspace_change.id,
    workspace_change.proposal_id,
    workspace_change.sort_index,
    workspace_change.entity_type,
    workspace_change.action,
    jsonb_build_object(
        'projectId', workspace_change.project_id,
        'targetId', workspace_change.target_id,
        'summary', workspace_change.summary
    ),
    COALESCE(workspace_change.payload_json, '{}'::jsonb),
    COALESCE(workspace_change.previous_json, '{}'::jsonb),
    COALESCE(workspace_change.payload_json, '{}'::jsonb),
    CASE
        WHEN workspace_change.previous_json IS NULL THEN '{}'::jsonb
        WHEN workspace_change.entity_type = 'WORK_ITEM' THEN
            jsonb_build_object('updatedAt', workspace_change.previous_json #>> '{workItem,updatedAt}')
        WHEN workspace_change.entity_type = 'ENTRY' THEN
            jsonb_build_object('updatedAt', workspace_change.previous_json ->> 'updatedAt')
        ELSE workspace_change.previous_json
    END,
    workspace_change.status,
    workspace_change.feedback,
    workspace_change.applied_at,
    workspace_change.created_at,
    workspace_change.updated_at
FROM workspace_change;

DROP TABLE workspace_change;
DROP TABLE workspace_change_proposal;

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
    title              VARCHAR(200) NOT NULL,
    description        TEXT,
    starts_at          TIMESTAMPTZ  NOT NULL,
    ends_at            TIMESTAMPTZ  NOT NULL,
    timezone           VARCHAR(100) NOT NULL,
    all_day            BOOLEAN      NOT NULL DEFAULT FALSE,
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

CREATE TABLE mail_notification
(
    id TEXT PRIMARY KEY,
    recipient_user_id TEXT NOT NULL,
    recipient_email TEXT NOT NULL,
    work_item_id TEXT,
    actor_user_id TEXT,
    body TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX mail_notification_ready_idx
    ON mail_notification (available_at, recipient_user_id) WHERE attempts < 3;
CREATE INDEX mail_notification_recipient_idx
    ON mail_notification (recipient_user_id, available_at) WHERE attempts < 3;

CREATE INDEX IF NOT EXISTS auth_session_user_exp_idx
    ON auth_session (user_id, expires_at);

CREATE INDEX IF NOT EXISTS team_join_request_pending_team_idx
    ON team_join_request (team_id, created_at) WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS mail_notification_created_at_idx
    ON mail_notification (created_at);
