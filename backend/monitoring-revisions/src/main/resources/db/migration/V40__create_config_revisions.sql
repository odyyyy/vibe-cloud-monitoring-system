-- Revision history for user-managed configuration resources (dashboards and alert rules).
-- A row stores the resource state AFTER the change, so the timeline answers "who changed it
-- to this?". The snapshot is a JSON document owned by the resource module and is validated
-- again before a restore. Rows are tenant-scoped and pruned to the latest 10 per resource.
CREATE TABLE config_revisions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT NOT NULL,
    revision_number INT NOT NULL,
    user_id BIGINT NULL,
    change_type VARCHAR(16) NOT NULL,
    restored_from_revision_number INT NULL,
    schema_version INT NOT NULL,
    snapshot LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_config_revisions_resource
        (organization_id, resource_type, resource_id, revision_number)
);
