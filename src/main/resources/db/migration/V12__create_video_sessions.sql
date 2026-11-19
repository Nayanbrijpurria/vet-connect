CREATE TABLE video_sessions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    appointment_id BIGINT NOT NULL UNIQUE REFERENCES appointments(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL DEFAULT 'LIVEKIT',
    room_name VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'ACTIVE', 'ENDED', 'EXPIRED')),
    scheduled_start TIMESTAMPTZ NOT NULL,
    scheduled_end TIMESTAMPTZ NOT NULL,
    actual_start TIMESTAMPTZ,
    actual_end TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_video_session_times CHECK (scheduled_end > scheduled_start)
);

CREATE INDEX idx_video_sessions_status_updated
    ON video_sessions(status, updated_at DESC);

CREATE TABLE video_session_participants (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    video_session_id BIGINT NOT NULL REFERENCES video_sessions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    participant_role VARCHAR(20) NOT NULL
        CHECK (participant_role IN ('CUSTOMER', 'VET')),
    join_timestamp TIMESTAMPTZ,
    leave_timestamp TIMESTAMPTZ,
    participant_identity VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_video_session_participant_identity
    ON video_session_participants(video_session_id, participant_identity);

CREATE INDEX idx_video_session_participants_session_join
    ON video_session_participants(video_session_id, join_timestamp DESC);

CREATE TABLE video_webhook_events (
    event_id VARCHAR(200) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    room_name VARCHAR(100),
    participant_identity VARCHAR(100),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
