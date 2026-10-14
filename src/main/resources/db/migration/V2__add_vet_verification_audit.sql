
-- Record who reviewed each veterinarian application and when.
ALTER TABLE vet_profiles
    ADD COLUMN reviewed_by BIGINT REFERENCES users(id),
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN rejection_reason TEXT;

-- Keep review information consistent with the verification status.
ALTER TABLE vet_profiles
    ADD CONSTRAINT chk_vet_review_details
        CHECK (
            (
                verification_status = 'PENDING'
                    AND reviewed_by IS NULL
                    AND reviewed_at IS NULL
                    AND rejection_reason IS NULL
                )
                OR
            (
                verification_status = 'APPROVED'
                    AND reviewed_by IS NOT NULL
                    AND reviewed_at IS NOT NULL
                    AND rejection_reason IS NULL
                )
                OR
            (
                verification_status = 'REJECTED'
                    AND reviewed_by IS NOT NULL
                    AND reviewed_at IS NOT NULL
                    AND rejection_reason IS NOT NULL
                    AND LENGTH(TRIM(rejection_reason)) > 0
                )
            );
