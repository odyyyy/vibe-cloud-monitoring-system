-- События логов, принятые от агентов.
-- Привязаны к приложению (tenant-скоуп) через application_id.
-- event_time — момент события (UTC), received_at — момент приёма на сервере (UTC).
-- message и context — недоверенный текст, хранится как есть (TEXT).
CREATE TABLE log_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    level VARCHAR(16) NOT NULL,
    message TEXT NOT NULL,
    context TEXT,
    event_time TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL,
    INDEX idx_log_events_app_event_time (application_id, event_time),
    CONSTRAINT fk_log_events_application
        FOREIGN KEY (application_id) REFERENCES applications (id)
        ON DELETE CASCADE
);
