-- Platform self-monitoring: the internal application holds the platform's own metrics.
-- System applications are readable by every authenticated tenant but contain no customer data.

ALTER TABLE applications ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_applications_is_system ON applications (is_system);
