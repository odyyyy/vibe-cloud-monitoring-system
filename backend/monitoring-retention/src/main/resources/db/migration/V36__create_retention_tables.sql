-- Per-organization telemetry retention policy and purge-run history.
-- One policy row per organization; the absence of a row means the configured defaults apply.
CREATE TABLE retention_policies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    logs_retention_days INT NOT NULL,
    metrics_retention_days INT NOT NULL,
    traces_retention_days INT NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by BIGINT,
    CONSTRAINT uq_retention_policies_organization UNIQUE (organization_id),
    CONSTRAINT fk_retention_policies_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id)
        ON DELETE CASCADE
);

-- Append-only history of purge attempts; only counts and a short error message are stored.
CREATE TABLE retention_purge_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    data_type VARCHAR(16) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP NOT NULL,
    rows_deleted BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(500),
    CONSTRAINT fk_retention_purge_runs_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_retention_purge_runs_org_started
    ON retention_purge_runs (organization_id, started_at);

-- The purge cuts by (application_id, event time). log_events already indexes
-- (application_id, event_time) and trace_spans already indexes (application_id, start_time);
-- metric_samples and metric_aggregates lack the equivalent access path, so add it here.
CREATE INDEX idx_metric_samples_app_event_time
    ON metric_samples (application_id, event_time);

CREATE INDEX idx_metric_aggregates_app_bucket
    ON metric_aggregates (application_id, bucket_epoch);
