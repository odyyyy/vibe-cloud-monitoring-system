-- Rule-based log processing pipeline. Rules are tenant-owned; application_id NULL means the
-- rule applies to every application in the organization. action_config is a JSON document whose
-- shape depends on the action and is validated by the application service.
CREATE TABLE log_pipeline_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    application_id BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    filter_expression VARCHAR(1024) NOT NULL,
    action VARCHAR(32) NOT NULL,
    action_config TEXT NULL,
    order_index INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log_pipeline_rules_org_order (organization_id, order_index),
    INDEX idx_log_pipeline_rules_application_id (application_id),
    CONSTRAINT fk_log_pipeline_rules_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_log_pipeline_rules_application
        FOREIGN KEY (application_id) REFERENCES applications (id)
        ON DELETE CASCADE
);
