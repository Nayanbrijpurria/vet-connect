CREATE TABLE auth_identities (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auth_identity_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_auth_identity_user_provider UNIQUE (user_id, provider)
);

