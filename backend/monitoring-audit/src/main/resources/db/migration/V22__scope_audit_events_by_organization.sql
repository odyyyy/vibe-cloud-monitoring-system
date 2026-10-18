-- Audit events gain an organization scope. user_id stays as the acting-user reference.
-- Existing events are attributed to the personal organization that V15 created for the user
-- (organization id equals user id at migration time).
ALTER TABLE audit_events ADD COLUMN organization_id BIGINT NOT NULL DEFAULT 0;
UPDATE audit_events SET organization_id = user_id;
CREATE INDEX idx_audit_events_organization_id ON audit_events (organization_id);
CREATE INDEX idx_audit_events_organization_action ON audit_events (organization_id, action);
