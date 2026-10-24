-- Scheduled reports / digests. Reports are tenant-owned and limited per organization by the
-- application service; the scheduler only reads rows whose time_of_day falls in the current hour.
CREATE TABLE scheduled_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    frequency VARCHAR(16) NOT NULL,
    day_of_week INT NULL,
    time_of_day TIME NOT NULL,
    channel_type VARCHAR(16) NOT NULL,
    channel_id BIGINT NULL,
    include_metrics BOOLEAN NOT NULL DEFAULT TRUE,
    include_top_errors BOOLEAN NOT NULL DEFAULT TRUE,
    include_uptime BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scheduled_reports_organization_id (organization_id),
    INDEX idx_scheduled_reports_time_of_day (time_of_day),
    CONSTRAINT fk_scheduled_reports_channel
        FOREIGN KEY (channel_id) REFERENCES notification_channels (id)
        ON DELETE SET NULL
);

CREATE TABLE scheduled_report_recipients (
    report_id BIGINT NOT NULL,
    recipient_order INT NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    PRIMARY KEY (report_id, recipient_order),
    CONSTRAINT fk_scheduled_report_recipients_report
        FOREIGN KEY (report_id) REFERENCES scheduled_reports (id)
        ON DELETE CASCADE
);

-- Scheduled reports reuse the notification delivery log, but do not always target a configured
-- notification channel (email digests send directly to validated recipients).
ALTER TABLE notification_deliveries MODIFY COLUMN channel_id BIGINT NULL;
ALTER TABLE notification_deliveries ADD COLUMN scheduled_report_id BIGINT NULL;
CREATE INDEX idx_notification_deliveries_scheduled_report_id
    ON notification_deliveries (scheduled_report_id);
