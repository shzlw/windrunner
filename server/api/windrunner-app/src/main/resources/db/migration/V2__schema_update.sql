ALTER TABLE chat_session
    ADD COLUMN title TEXT;

ALTER TABLE team
    ADD COLUMN description TEXT;

ALTER TABLE app_user
    ADD COLUMN job_title TEXT,
    ADD COLUMN bio TEXT;
