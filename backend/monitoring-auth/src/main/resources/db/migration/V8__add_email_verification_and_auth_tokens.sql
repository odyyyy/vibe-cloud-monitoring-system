-- Email verification support for the identity module (control plane).
-- Schema is owned by Flyway; Hibernate only validates the mapping.

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP NULL;

-- Accounts created before email verification existed are grandfathered in as
-- verified so that the new login rule does not lock out existing users.
UPDATE users
SET email_verified = TRUE,
    email_verified_at = CURRENT_TIMESTAMP
WHERE email_verified = FALSE;

-- Single-use, hashed tokens for email confirmation and password reset.
-- Only the SHA-256 hash of the token is stored; the raw value is emailed once.
CREATE TABLE auth_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_auth_tokens_token_hash (token_hash),
    INDEX idx_auth_tokens_user_id (user_id),
    CONSTRAINT fk_auth_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
);
