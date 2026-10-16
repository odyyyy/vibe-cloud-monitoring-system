-- Точки метрик, принятые от агентов.
-- Привязаны к приложению (tenant-скоуп) через application_id.
-- event_time — момент измерения (UTC), received_at — момент приёма на сервере (UTC).
CREATE TABLE metric_samples (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    metric_value DOUBLE NOT NULL,
    event_time TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL,
    INDEX idx_metric_samples_app_name_time (application_id, name, event_time),
    CONSTRAINT fk_metric_samples_application
        FOREIGN KEY (application_id) REFERENCES applications (id)
        ON DELETE CASCADE
);

-- Метки метрик (tags). Хранятся отдельными строками, чтобы не парсить JSON в приложении.
CREATE TABLE metric_sample_tags (
    metric_sample_id BIGINT NOT NULL,
    tag_key VARCHAR(100) NOT NULL,
    tag_value VARCHAR(500),
    PRIMARY KEY (metric_sample_id, tag_key),
    CONSTRAINT fk_metric_sample_tags_sample
        FOREIGN KEY (metric_sample_id) REFERENCES metric_samples (id)
        ON DELETE CASCADE
);
