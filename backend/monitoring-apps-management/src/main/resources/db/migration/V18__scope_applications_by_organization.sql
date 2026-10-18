-- Tenant scoping for applications.
-- Existing rows already belong to a single user; V15 created an organization with the same id
-- as each user, so renaming the column preserves ownership without a data rewrite.

ALTER TABLE applications DROP INDEX idx_applications_owner_id;
ALTER TABLE applications RENAME COLUMN owner_id TO organization_id;
CREATE INDEX idx_applications_organization_id ON applications (organization_id);
