-- Clarifying answers are kept on the trip so regenerate and chat refine reuse them.
ALTER TABLE trips
    ADD COLUMN planning_answers JSONB NOT NULL DEFAULT '{}'::jsonb;

-- Rotating refresh tokens (only the SHA-256 hash is stored). A family is one login session; reusing a rotated
-- token revokes the whole family.
CREATE TABLE refresh_tokens (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    family_id     VARCHAR(36)  NOT NULL,
    token_hash    VARCHAR(64)  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    revoked_at    TIMESTAMPTZ,
    revoke_reason VARCHAR(20),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_revoke_reason
        CHECK (revoke_reason IS NULL OR revoke_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED'))
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
