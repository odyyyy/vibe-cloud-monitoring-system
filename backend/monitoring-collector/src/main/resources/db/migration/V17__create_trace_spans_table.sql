-- Span'ы распределённой трассировки, принятые от агентов.
-- Привязаны к приложению (tenant-скоуп) через application_id.
-- start_time — момент начала спана (UTC), end_time — вычисленный конец (start_time + duration_ms)
-- (хранится отдельно, чтобы агрегировать длительность трассы одной переносимой SQL-выражением).
CREATE TABLE trace_spans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL,
    span_id VARCHAR(64) NOT NULL,
    parent_span_id VARCHAR(64),
    application_id BIGINT NOT NULL,
    service_name VARCHAR(128) NOT NULL,
    operation_name VARCHAR(256) NOT NULL,
    start_time TIMESTAMP(3) NOT NULL,
    end_time TIMESTAMP(3) NOT NULL,
    duration_ms BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attributes TEXT,
    INDEX idx_trace_spans_trace (trace_id),
    INDEX idx_trace_spans_app_start (application_id, start_time),
    INDEX idx_trace_spans_app_service (application_id, service_name),
    CONSTRAINT fk_trace_spans_application
        FOREIGN KEY (application_id) REFERENCES applications (id)
        ON DELETE CASCADE
);
