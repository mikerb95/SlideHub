-- V9: create questions table for stream viewer Q&A
-- viewer_token: ephemeral token from Redis; loose reference (no FK) because viewers are not in the DB
-- display_name: cached at submission time; NULL when anonymous=true
-- status: PENDING | ANSWERED | DISMISSED — managed by the presenter

CREATE TABLE IF NOT EXISTS questions (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    session_id   VARCHAR(36)  NOT NULL REFERENCES presentation_sessions(id) ON DELETE CASCADE,
    viewer_token VARCHAR(120),
    display_name VARCHAR(100),
    anonymous    BOOLEAN      NOT NULL DEFAULT FALSE,
    content      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    upvotes      INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_questions_session_id ON questions(session_id);
CREATE INDEX IF NOT EXISTS idx_questions_status     ON questions(session_id, status);
