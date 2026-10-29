CREATE TABLE vet_application_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vet_user_id BIGINT NOT NULL REFERENCES users(id),
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    action VARCHAR(20) NOT NULL CHECK (action IN (
        'SUBMITTED',
        'RESUBMITTED',
        'APPROVED',
        'REJECTED',
        'SUSPENDED',
        'REINSTATED'
    )),
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vet_application_history_vet_created
    ON vet_application_history(vet_user_id, created_at DESC);

-- Preserve the latest known state as a starting point for existing applications.
INSERT INTO vet_application_history (
    vet_user_id,
    actor_user_id,
    action,
    from_status,
    to_status,
    reason,
    created_at
)
SELECT
    user_id,
    COALESCE(reviewed_by, user_id),
    CASE verification_status
        WHEN 'PENDING' THEN 'SUBMITTED'
        WHEN 'APPROVED' THEN 'APPROVED'
        WHEN 'REJECTED' THEN 'REJECTED'
        WHEN 'SUSPENDED' THEN 'SUSPENDED'
    END,
    NULL,
    verification_status,
    rejection_reason,
    COALESCE(reviewed_at, created_at)
FROM vet_profiles;
