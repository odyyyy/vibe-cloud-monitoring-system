-- Hidden platform tenant that owns self-monitoring data. It has no memberships, so it never
-- appears in a user's organization list.

ALTER TABLE organizations ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE;
