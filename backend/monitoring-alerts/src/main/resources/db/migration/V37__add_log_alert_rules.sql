-- Log-based alert rules. The existing `type` column gains the value 'LOG'; metric and
-- health-check rules keep their current values, so no row rewrite or default is required.
-- The new columns are nullable so existing rules stay valid and are only populated for LOG rules.
ALTER TABLE alert_rules ADD COLUMN log_level VARCHAR(16) NULL;
ALTER TABLE alert_rules ADD COLUMN log_query VARCHAR(200) NULL;
ALTER TABLE alert_rules ADD COLUMN window_minutes INT NULL;
ALTER TABLE alert_rules ADD COLUMN comparison VARCHAR(20) NULL;
ALTER TABLE alert_rules ADD COLUMN count_threshold BIGINT NULL;

-- Listing and counting the organization's active log rules backs the per-tenant rule limit.
CREATE INDEX idx_alert_rules_org_type_active
    ON alert_rules (organization_id, type, is_active);
