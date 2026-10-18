-- Узлы агентов (agent fleet), сгруппированные по приложению (tenant-скоуп) через application_id.
-- Строка создаётся и обновляется при приёме телеметрии: hostname берётся из тега "host",
-- agent_version — из тега "version" (может отсутствовать у старых агентов).
-- last_seen_at — момент последнего принятого батча (UTC); по нему вычисляется Online/Offline.
-- Уникальный ключ (application_id, hostname) не даёт завести двух агентов с одного хоста,
-- поэтому повторный приём метрик только обновляет last_seen_at.
CREATE TABLE agent_nodes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    agent_version VARCHAR(64),
    last_seen_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_agent_nodes_application
        FOREIGN KEY (application_id) REFERENCES applications (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_agent_nodes_application_hostname
        UNIQUE (application_id, hostname)
);

-- Ускоряет выборку агентов приложения, упорядоченную по последней активности.
CREATE INDEX idx_agent_nodes_app_last_seen ON agent_nodes (application_id, last_seen_at);
