-- Tenant scoping for synthetic health checks. Ownership moves from a user to an organization.
ALTER TABLE health_checks DROP INDEX idx_health_checks_owner_id;
ALTER TABLE health_checks RENAME COLUMN owner_id TO organization_id;
CREATE INDEX idx_health_checks_organization_id ON health_checks (organization_id);
