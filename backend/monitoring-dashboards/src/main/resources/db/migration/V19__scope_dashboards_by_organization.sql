-- Tenant scoping for dashboards. Ownership moves from a user to an organization.
ALTER TABLE dashboards DROP INDEX idx_dashboards_owner_id;
ALTER TABLE dashboards RENAME COLUMN owner_id TO organization_id;
CREATE INDEX idx_dashboards_organization_id ON dashboards (organization_id);
