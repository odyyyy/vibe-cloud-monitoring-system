-- Tenant scoping for alerting. Rules, channels and incidents now belong to an organization.
ALTER TABLE alert_rules DROP INDEX idx_alert_rules_owner_id;
ALTER TABLE alert_rules RENAME COLUMN owner_id TO organization_id;
CREATE INDEX idx_alert_rules_organization_id ON alert_rules (organization_id);

ALTER TABLE notification_channels DROP INDEX idx_notification_channels_owner_id;
ALTER TABLE notification_channels RENAME COLUMN owner_id TO organization_id;
CREATE INDEX idx_notification_channels_organization_id ON notification_channels (organization_id);

ALTER TABLE alert_incidents DROP INDEX idx_alert_incidents_owner_id;
ALTER TABLE alert_incidents RENAME COLUMN owner_id TO organization_id;
CREATE INDEX idx_alert_incidents_organization_id ON alert_incidents (organization_id);
