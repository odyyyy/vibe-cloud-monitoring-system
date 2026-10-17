-- Пользовательские правила алертинга (control plane). Принадлежат владельцу по owner_id.
-- target хранит имя метрики (для METRIC_THRESHOLD) или id health-check'а (для HEALTH_CHECK).
-- Схема согласована с сущностью AlertRule.
CREATE TABLE alert_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    application_id BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(32) NOT NULL,
    target VARCHAR(255) NOT NULL,
    condition_type VARCHAR(16) NOT NULL,
    threshold_value DOUBLE NOT NULL,
    duration_minutes INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    breach_started_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_alert_rules_owner_id (owner_id),
    INDEX idx_alert_rules_is_active (is_active),
    CONSTRAINT fk_alert_rules_application
        FOREIGN KEY (application_id) REFERENCES applications (id)
        ON DELETE CASCADE
);

-- Каналы уведомлений (только WEBHOOK в MVP). URL проходит SSRF-проверку перед сохранением.
CREATE TABLE notification_channels (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_notification_channels_owner_id (owner_id)
);

-- Инциденты алертов: активные (FIRING) и завершённые (RESOLVED).
CREATE TABLE alert_incidents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    triggered_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP NULL,
    INDEX idx_alert_incidents_rule_id (rule_id),
    INDEX idx_alert_incidents_owner_id (owner_id),
    INDEX idx_alert_incidents_triggered_at (triggered_at),
    CONSTRAINT fk_alert_incidents_rule
        FOREIGN KEY (rule_id) REFERENCES alert_rules (id)
        ON DELETE CASCADE
);
