-- Daily telemetry usage rollup per organization (Usage & Billing page).
-- One row per (organization, day); counts are the number of telemetry items whose event
-- time falls into that UTC day. The rollup recomputes absolute values, so a repeated run
-- simply overwrites the row and can never double count.
CREATE TABLE daily_usage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    metrics_count BIGINT NOT NULL DEFAULT 0,
    logs_count BIGINT NOT NULL DEFAULT 0,
    traces_count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_daily_usage_organization_date UNIQUE (organization_id, usage_date)
);
