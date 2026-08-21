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