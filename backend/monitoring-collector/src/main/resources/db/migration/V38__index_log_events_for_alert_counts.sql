-- Log alert evaluation runs COUNT(*) over (application_id, event_time) with an optional
-- level filter. The existing (application_id, event_time) index covers the range scan; this
-- covering index additionally avoids a row lookup when a level predicate is present.
CREATE INDEX idx_log_events_app_time_level
    ON log_events (application_id, event_time, level);
