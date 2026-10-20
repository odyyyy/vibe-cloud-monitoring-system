-- Built-in dashboards are managed by the platform: every tenant can read them,
-- but they can never be modified or deleted through the dashboard API.

ALTER TABLE dashboards ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_dashboards_is_system ON dashboards (is_system);
