-- Incident lifecycle: acknowledgment, manual resolution attribution and team notes.
-- Status values are FIRING, ACKNOWLEDGED and RESOLVED; the column already fits the longest
-- value ("ACKNOWLEDGED" is 12 characters in a VARCHAR(16)).
ALTER TABLE alert_incidents ADD COLUMN acknowledged_by BIGINT NULL;
ALTER TABLE alert_incidents ADD COLUMN acknowledged_at TIMESTAMP NULL;
ALTER TABLE alert_incidents ADD COLUMN resolved_by BIGINT NULL;
ALTER TABLE alert_incidents ADD COLUMN notes VARCHAR(1000) NULL;

-- The incidents page filters by status within the active organization.
CREATE INDEX idx_alert_incidents_org_status
    ON alert_incidents (organization_id, status);
