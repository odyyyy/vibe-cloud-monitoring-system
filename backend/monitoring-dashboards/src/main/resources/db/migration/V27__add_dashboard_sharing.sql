-- Public read-only sharing for dashboards.
-- share_token: cryptographically secure UUID (v4)
-- is_public: flag indicating dashboard is accessible via public link
ALTER TABLE dashboards
    ADD COLUMN share_token VARCHAR(36) NULL;

ALTER TABLE dashboards
    ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX idx_dashboards_share_token ON dashboards (share_token);