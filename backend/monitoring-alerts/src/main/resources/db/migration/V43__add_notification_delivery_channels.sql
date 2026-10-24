-- Additional notification channels: generic webhook signing/headers, PagerDuty and Teams.
-- Secrets are stored with the same mechanism as existing Slack webhook URLs (plaintext in the
-- application database) and are masked in every API response.
ALTER TABLE notification_channels ADD COLUMN teams_webhook_url VARCHAR(2048) NULL;
ALTER TABLE notification_channels ADD COLUMN pagerduty_routing_key VARCHAR(255) NULL;
ALTER TABLE notification_channels ADD COLUMN webhook_secret VARCHAR(255) NULL;
ALTER TABLE notification_channels ADD COLUMN webhook_headers VARCHAR(4096) NULL;

-- Delivery log with retries. Rows are never deleted when a channel is removed, so the history
-- of delivery failures remains available to the tenant.
CREATE TABLE notification_deliveries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    incident_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_http_status INT NULL,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMP NULL,
    INDEX idx_notification_deliveries_organization_id (organization_id),
    INDEX idx_notification_deliveries_org_created (organization_id, created_at),
    INDEX idx_notification_deliveries_channel_id (channel_id),
    INDEX idx_notification_deliveries_incident_id (incident_id)
);
