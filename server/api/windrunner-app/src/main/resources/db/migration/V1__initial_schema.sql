CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE app_user
(
    id                   TEXT PRIMARY KEY,
    username             TEXT NOT NULL UNIQUE,
    email                TEXT UNIQUE,
    display_name         TEXT,
    timezone             TEXT NOT NULL DEFAULT 'UTC',
    password_hash        TEXT NOT NULL,
    status               TEXT NOT NULL,
    global_role          TEXT NOT NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at           TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE auth_session
(
    id                 TEXT PRIMARY KEY,
    user_id            TEXT NOT NULL,
    session_token_hash TEXT NOT NULL,
    csrf_token         TEXT NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at         TIMESTAMPTZ DEFAULT NOW() NOT NULL,

    UNIQUE (session_token_hash)
);

CREATE TABLE api_key
(
    id            TEXT PRIMARY KEY,
    owner_user_id TEXT NOT NULL,
    name          TEXT NOT NULL,
    key_hash      TEXT NOT NULL,
    status        TEXT NOT NULL,
    created_at    TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    last_used_at  TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,

    UNIQUE (key_hash)
);

CREATE INDEX api_key_owner_idx
    ON api_key (owner_user_id, created_at DESC);

CREATE TABLE api_key_scope
(
    api_key_id TEXT NOT NULL,
    scope      TEXT NOT NULL,
    PRIMARY KEY (api_key_id, scope)
);

CREATE TABLE project
(
    id                 TEXT PRIMARY KEY,
    name               TEXT NOT NULL,
    created_by_user_id TEXT NOT NULL,
    created_at         TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at         TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    archived_at        TIMESTAMPTZ
);

CREATE TABLE team
(
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE TABLE team_member
(
    team_id    TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    role       TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    PRIMARY KEY (team_id, user_id)
);

CREATE INDEX team_member_user_idx
    ON team_member (user_id, team_id);

CREATE TABLE team_join_request
(
    id                 TEXT PRIMARY KEY,
    team_id            TEXT NOT NULL,
    user_id            TEXT NOT NULL,
    status             TEXT NOT NULL,
    created_at         TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    decided_at         TIMESTAMPTZ,
    decided_by_user_id TEXT
);

CREATE UNIQUE INDEX team_join_request_pending_user_idx
    ON team_join_request (team_id, user_id)
    WHERE status = 'PENDING';

CREATE TABLE project_member
(
    project_id TEXT NOT NULL,
    user_id    TEXT NOT NULL,
    role       TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    PRIMARY KEY (project_id, user_id)
);

CREATE INDEX project_member_user_idx
    ON project_member (user_id, project_id);

CREATE TABLE project_team
(
    project_id TEXT NOT NULL,
    team_id    TEXT NOT NULL,
    role       TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    PRIMARY KEY (project_id, team_id)
);

CREATE INDEX project_team_team_idx
    ON project_team (team_id, project_id);

CREATE TABLE chat_session
(
    id          TEXT PRIMARY KEY,
    project_id  TEXT NOT NULL,
    user_id     TEXT NOT NULL,
    status      TEXT NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    updated_at  TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    archived_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX chat_session_active_user_project_idx
    ON chat_session (project_id, user_id)
    WHERE status = 'ACTIVE';

CREATE TABLE chat_message
(
    id              TEXT PRIMARY KEY,
    chat_session_id TEXT NOT NULL,
    role            TEXT NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

CREATE INDEX chat_message_session_idx
    ON chat_message (chat_session_id, created_at, id);

CREATE TABLE work_item
(
    id                  TEXT PRIMARY KEY,
    project_id          TEXT NOT NULL,
    parent_work_item_id TEXT,
    sort_index          INTEGER NOT NULL DEFAULT 0,
    type                TEXT NOT NULL,
    title               TEXT NOT NULL,
    status              TEXT NOT NULL DEFAULT 'OPEN',
    due_date            DATE,
    priority            TEXT,
    created_by_user_id  TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    search_vec          TSVECTOR
);

CREATE INDEX work_item_project_outline_idx
    ON work_item (project_id, parent_work_item_id, sort_index, id);

CREATE INDEX work_item_project_status_idx
    ON work_item (project_id, status, updated_at DESC);

CREATE INDEX work_item_search_vec_idx
    ON work_item USING GIN (search_vec);

CREATE INDEX work_item_title_trgm_idx
    ON work_item USING GIN (title gin_trgm_ops);

CREATE TABLE work_item_assignee
(
    id            TEXT PRIMARY KEY,
    work_item_id  TEXT NOT NULL,
    assignee_type TEXT NOT NULL,
    assignee_id   TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (work_item_id, assignee_type, assignee_id)
);

CREATE INDEX work_item_assignee_lookup_idx
    ON work_item_assignee (assignee_type, assignee_id, work_item_id);

CREATE TABLE entry
(
    id             TEXT PRIMARY KEY,
    project_id     TEXT NOT NULL,
    work_item_id   TEXT NOT NULL,
    sort_index     INTEGER NOT NULL,
    author_user_id TEXT NOT NULL,
    type           TEXT NOT NULL,
    body           TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    search_vec     TSVECTOR
);

CREATE INDEX entry_work_item_order_idx
    ON entry (work_item_id, sort_index, id);

CREATE INDEX entry_project_idx
    ON entry (project_id, created_at DESC);

CREATE INDEX entry_search_vec_idx
    ON entry USING GIN (search_vec);

CREATE INDEX entry_body_trgm_idx
    ON entry USING GIN (body gin_trgm_ops);

CREATE TABLE relationship
(
    id               TEXT PRIMARY KEY,
    project_id       TEXT NOT NULL,
    from_entity_type TEXT NOT NULL,
    from_entity_id   TEXT NOT NULL,
    to_entity_type   TEXT NOT NULL,
    to_entity_id     TEXT NOT NULL,
    type             TEXT NOT NULL,
    reason           TEXT,
    source_entry_id  TEXT,
    created_by_user_id TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    search_vec       TSVECTOR
);

CREATE INDEX relationship_project_from_idx
    ON relationship (project_id, from_entity_type, from_entity_id, type);

CREATE INDEX relationship_project_to_idx
    ON relationship (project_id, to_entity_type, to_entity_id, type);

CREATE INDEX relationship_search_vec_idx
    ON relationship USING GIN (search_vec);

CREATE INDEX relationship_reason_trgm_idx
    ON relationship USING GIN (reason gin_trgm_ops);

CREATE TABLE workspace_change_proposal
(
    id                TEXT PRIMARY KEY,
    project_id        TEXT NOT NULL,
    chat_session_id   TEXT NOT NULL,
    source_message_id TEXT NOT NULL,
    source_text       TEXT NOT NULL,
    status            TEXT NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX workspace_change_proposal_project_idx
    ON workspace_change_proposal (project_id, created_at DESC);

CREATE TABLE workspace_change
(
    id               TEXT PRIMARY KEY,
    proposal_id      TEXT NOT NULL,
    project_id       TEXT NOT NULL,
    sort_index       INTEGER NOT NULL,
    entity_type      TEXT NOT NULL,
    action           TEXT NOT NULL,
    target_id        TEXT NOT NULL,
    summary          TEXT NOT NULL,
    payload_json     JSONB,
    previous_json    JSONB,
    status           TEXT NOT NULL DEFAULT 'PENDING',
    feedback         TEXT,
    applied_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX workspace_change_proposal_order_idx
    ON workspace_change (proposal_id, sort_index, id);

CREATE INDEX workspace_change_project_status_idx
    ON workspace_change (project_id, status, created_at DESC);

CREATE TABLE audit_log
(
    id             TEXT PRIMARY KEY,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_user_id  TEXT,
    action         TEXT NOT NULL,
    entity_type    TEXT NOT NULL,
    entity_id      TEXT,
    project_id     TEXT,
    outcome        TEXT NOT NULL DEFAULT 'SUCCESS',
    summary        TEXT NOT NULL,
    before_json    JSONB,
    after_json     JSONB,
    changes_json   JSONB,
    metadata_json  JSONB
);

CREATE INDEX audit_log_occurred_at_idx
    ON audit_log (occurred_at DESC);

CREATE INDEX audit_log_actor_idx
    ON audit_log (actor_user_id, occurred_at DESC);

CREATE INDEX audit_log_entity_idx
    ON audit_log (entity_type, entity_id, occurred_at DESC);

CREATE INDEX audit_log_project_idx
    ON audit_log (project_id, occurred_at DESC);

CREATE TABLE llm_usage
(
    id            TEXT PRIMARY KEY,
    user_id       TEXT,
    project_id    TEXT,
    feature       TEXT NOT NULL,
    provider      TEXT NOT NULL,
    model         TEXT,
    input_tokens  BIGINT,
    output_tokens BIGINT,
    total_tokens  BIGINT,
    outcome       TEXT NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,
    duration_ms   BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX llm_usage_created_idx
    ON llm_usage (created_at);

CREATE INDEX llm_usage_project_idx
    ON llm_usage (project_id, created_at);

CREATE INDEX llm_usage_user_idx
    ON llm_usage (user_id, created_at);

CREATE TABLE user_setting
(
    id         TEXT PRIMARY KEY,
    user_id    TEXT NOT NULL,
    key        TEXT NOT NULL,
    value      JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, key)
);

CREATE INDEX user_setting_user_idx
    ON user_setting (user_id, key);

CREATE TABLE work_item_subscription
(
    id           TEXT PRIMARY KEY,
    user_id      TEXT NOT NULL,
    project_id   TEXT NOT NULL,
    work_item_id TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, work_item_id)
);

CREATE INDEX work_item_subscription_user_idx
    ON work_item_subscription (user_id, created_at DESC, id);

CREATE TABLE user_notification
(
    id                TEXT PRIMARY KEY,
    recipient_user_id TEXT NOT NULL,
    notification_type TEXT NOT NULL,
    actor_user_id     TEXT,
    project_id        TEXT,
    work_item_id      TEXT,
    title             TEXT NOT NULL,
    message           TEXT NOT NULL,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX user_notification_recipient_created_idx
    ON user_notification (recipient_user_id, created_at DESC, id DESC);

CREATE INDEX user_notification_unread_idx
    ON user_notification (recipient_user_id, created_at DESC)
    WHERE read_at IS NULL;
