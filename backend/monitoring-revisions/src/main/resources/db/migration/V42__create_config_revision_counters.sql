-- Allocates the revision numbers of a configuration resource.
--
-- The counter is incremented with a single atomic UPDATE, so two concurrent saves of the same
-- dashboard or alert rule cannot allocate the same revision number. The row lock of the
-- counter also serializes the writers of a resource.
CREATE TABLE config_revision_counters (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT NOT NULL,
    last_number INT NOT NULL,
    CONSTRAINT uk_config_revision_counter
        UNIQUE (organization_id, resource_type, resource_id),
    INDEX idx_config_revision_counters_resource
        (organization_id, resource_type, resource_id)
);
