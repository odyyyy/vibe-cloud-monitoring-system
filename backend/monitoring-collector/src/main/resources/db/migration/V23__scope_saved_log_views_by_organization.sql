-- Tenant scoping for saved log views. Ownership moves from a user to an organization.
ALTER TABLE saved_log_views DROP INDEX idx_saved_log_views_owner_id;
ALTER TABLE saved_log_views RENAME COLUMN owner_id TO organization_id;
CREATE INDEX idx_saved_log_views_organization_id ON saved_log_views (organization_id);
