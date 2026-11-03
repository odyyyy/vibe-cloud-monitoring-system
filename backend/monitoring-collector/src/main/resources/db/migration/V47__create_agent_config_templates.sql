-- Centralized agent configuration templates. A template is tenant-owned and contains a
-- YAML document with the subset of agent settings that can be applied without a restart.
CREATE TABLE agent_config_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    config_yaml TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_agent_config_templates_org_name UNIQUE (organization_id, name)
);

CREATE INDEX idx_agent_config_templates_org_default
    ON agent_config_templates (organization_id, is_default);

-- One optional application-specific template. When absent, the organization default (if any)
-- is used. The application row already carries the tenant boundary, so it is not duplicated.
CREATE TABLE agent_config_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_agent_config_assignments_application
        FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_config_assignments_template
        FOREIGN KEY (template_id) REFERENCES agent_config_templates (id),
    CONSTRAINT uq_agent_config_assignments_application UNIQUE (application_id)
);

CREATE INDEX idx_agent_config_assignments_template
    ON agent_config_assignments (template_id);

-- Last config sync state reported by each agent. UNKNOWN is used until the agent reports.
ALTER TABLE agent_nodes ADD COLUMN config_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE agent_nodes ADD COLUMN config_version VARCHAR(64);
ALTER TABLE agent_nodes ADD COLUMN config_error VARCHAR(1024);
ALTER TABLE agent_nodes ADD COLUMN config_reported_at TIMESTAMP;
