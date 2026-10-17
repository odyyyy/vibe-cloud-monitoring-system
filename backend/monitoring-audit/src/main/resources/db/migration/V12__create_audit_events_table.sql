-- Immutable audit trail of security-relevant actions (control plane).
-- Append-only: rows are inserted by the application and never updated or deleted through the
-- API. user_id is the tenant scope used to restrict reads to the acting user.
-- event_time is the moment the action completed (UTC).
CREATE TABLE audit_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    ip_address VARCHAR(64) NULL,
    event_time TIMESTAMP NOT NULL,
    INDEX idx_audit_events_user_id (user_id),
    INDEX idx_audit_events_user_action (user_id, action),
    INDEX idx_audit_events_event_time (event_time)
);
