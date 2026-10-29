ALTER TABLE users
    ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT chk_users_account_status
        CHECK (account_status IN ('ACTIVE', 'DISABLED'));

ALTER TABLE vet_profiles
    DROP CONSTRAINT IF EXISTS vet_profiles_verification_status_check,
    ADD CONSTRAINT chk_vet_profiles_verification_status
        CHECK (verification_status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'));

ALTER TABLE vet_profiles
    DROP CONSTRAINT IF EXISTS chk_vet_review_details,
    ADD CONSTRAINT chk_vet_review_details
        CHECK (
            (verification_status = 'PENDING'
                AND reviewed_by IS NULL
                AND reviewed_at IS NULL
                AND rejection_reason IS NULL)
            OR
            (verification_status = 'APPROVED'
                AND reviewed_by IS NOT NULL
                AND reviewed_at IS NOT NULL
                AND rejection_reason IS NULL)
            OR
            (verification_status IN ('REJECTED', 'SUSPENDED')
                AND reviewed_by IS NOT NULL
                AND reviewed_at IS NOT NULL
                AND rejection_reason IS NOT NULL
                AND LENGTH(TRIM(rejection_reason)) > 0)
        );

CREATE TABLE admin_account_actions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_user_id BIGINT NOT NULL REFERENCES users(id),
    target_user_id BIGINT NOT NULL REFERENCES users(id),
    action VARCHAR(30) NOT NULL CHECK (action IN (
        'ACCOUNT_DISABLED',
        'ACCOUNT_ENABLED',
        'VET_SUSPENDED',
        'VET_REINSTATED'
    )),
    reason TEXT NOT NULL CHECK (LENGTH(TRIM(reason)) > 0),
    performed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_admin_account_actions_target
    ON admin_account_actions(target_user_id, performed_at DESC);
