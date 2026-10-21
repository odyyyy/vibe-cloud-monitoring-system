-- Built-in dashboards get a stable type so the platform can find them without relying on
-- names or ids. Existing system dashboards are the platform health dashboard.

ALTER TABLE dashboards ADD COLUMN system_dashboard_type VARCHAR(64);

UPDATE dashboards
SET system_dashboard_type = 'PLATFORM_HEALTH'
WHERE is_system = TRUE AND system_dashboard_type IS NULL;

CREATE UNIQUE INDEX idx_dashboards_system_dashboard_type
    ON dashboards (system_dashboard_type);
