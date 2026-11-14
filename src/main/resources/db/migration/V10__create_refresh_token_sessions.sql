CREATE TABLE refresh_token_sessions (
    session_id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_session_id UUID REFERENCES refresh_token_sessions(session_id) ON DELETE SET NULL,
    CONSTRAINT chk_refresh_session_expiry CHECK (expires_at > created_at),
    CONSTRAINT chk_refresh_session_not_self_replaced CHECK (
        replaced_by_session_id IS NULL OR replaced_by_session_id <> session_id
    )
);

CREATE INDEX idx_refresh_sessions_user_active
    ON refresh_token_sessions(user_id, expires_at DESC)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_refresh_sessions_family
    ON refresh_token_sessions(family_id);

CREATE INDEX idx_refresh_sessions_expiry
    ON refresh_token_sessions(expires_at)
    WHERE revoked_at IS NULL;
