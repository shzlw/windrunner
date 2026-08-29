ALTER TABLE chat_session
    ADD COLUMN title TEXT;

ALTER TABLE team
    ADD COLUMN description TEXT;

ALTER TABLE app_user
    ADD COLUMN title TEXT,
    ADD COLUMN bio TEXT;

DROP INDEX IF EXISTS chat_session_active_user_project_idx;

-- 2.0 sessions are user-scoped; project scope lives only in chat_session_context.
ALTER TABLE chat_session
    DROP COLUMN IF EXISTS project_id;

CREATE INDEX IF NOT EXISTS chat_session_user_status_idx
    ON chat_session (user_id, status, updated_at DESC, id);

CREATE INDEX IF NOT EXISTS chat_session_user_updated_idx
    ON chat_session (user_id, updated_at DESC, id);

CREATE TABLE IF NOT EXISTS chat_session_context
(
    id              TEXT PRIMARY KEY,
    chat_session_id TEXT NOT NULL,
    entity_type     TEXT NOT NULL,
    entity_id       TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (chat_session_id, entity_type, entity_id)
);

CREATE INDEX IF NOT EXISTS chat_session_context_session_idx
    ON chat_session_context (chat_session_id, created_at, id);

CREATE INDEX IF NOT EXISTS chat_session_context_entity_idx
    ON chat_session_context (entity_type, entity_id, chat_session_id);
