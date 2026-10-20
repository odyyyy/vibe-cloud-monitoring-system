-- V27__create_personal_access_tokens_table.sql
-- Personal Access Tokens for user-scoped API authentication.

CREATE TABLE personal_access_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tokens_user_id (user_id),
    INDEX idx_tokens_org_id (organization_id),
    INDEX idx_tokens_hash (token_hash)
);

CREATE TABLE personal_access_token_scopes (
    token_id BIGINT NOT NULL,
    scope VARCHAR(64) NOT NULL,
    PRIMARY KEY (token_id, scope),
    INDEX idx_scopes_token (token_id),
    CONSTRAINT fk_scopes_token
        FOREIGN KEY (token_id) REFERENCES personal_access_tokens(id)
        ON DELETE CASCADE
);