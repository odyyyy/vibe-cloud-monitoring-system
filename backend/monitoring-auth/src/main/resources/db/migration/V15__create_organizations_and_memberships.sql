-- Organizations (tenants) and memberships with roles.
-- The schema is owned by Flyway; Hibernate only validates the mapping.
--
-- Every existing user becomes the OWNER of a personal organization whose id equals the user
-- id. Resource migrations (V16..V20) reuse that id to backfill `organization_id`, which keeps
-- the upgrade lossless without an application-side data migration.

CREATE TABLE organizations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE memberships (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_memberships_organization_user UNIQUE (organization_id, user_id),
    INDEX idx_memberships_organization_id (organization_id),
    INDEX idx_memberships_user_id (user_id),
    CONSTRAINT fk_memberships_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_memberships_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

INSERT INTO organizations (id, name, created_at)
SELECT id, CONCAT(username, ' organization'), CURRENT_TIMESTAMP
FROM users;

INSERT INTO memberships (organization_id, user_id, role, created_at)
SELECT id, id, 'OWNER', CURRENT_TIMESTAMP
FROM users;
