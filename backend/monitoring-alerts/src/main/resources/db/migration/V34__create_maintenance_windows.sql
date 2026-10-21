-- Alert maintenance windows (muting). A one-time window suppresses incident creation and
-- notifications for all alerts, one application or one specific rule of the organization.
-- Times are UTC; the evaluator matches [start_time, end_time) against the evaluation instant.
CREATE TABLE maintenance_windows (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id BIGINT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_maintenance_windows_organization_id (organization_id),
    INDEX idx_maintenance_windows_org_active_time
        (organization_id, is_active, start_time, end_time)
);
