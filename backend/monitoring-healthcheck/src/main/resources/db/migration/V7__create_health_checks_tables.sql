-- Пользовательские синтетические health-check'и (control plane). Принадлежат владельцу по owner_id.
-- Схема согласована с сущностями HealthCheckConfig / HealthCheckResult.
CREATE TABLE health_checks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    target_url VARCHAR(2048) NOT NULL,
    method VARCHAR(8) NOT NULL,
    expected_status_code INT NOT NULL,
    interval_seconds INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_checked_at TIMESTAMP NULL,
    INDEX idx_health_checks_owner_id (owner_id)
);

-- История результатов проверок. error_message — недоверенный текст из внешнего сервиса.
CREATE TABLE health_check_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_time_ms INT NOT NULL,
    error_message VARCHAR(1024) NULL,
    checked_at TIMESTAMP NOT NULL,
    INDEX idx_health_check_results_config_id (config_id),
    INDEX idx_health_check_results_checked_at (checked_at),
    CONSTRAINT fk_health_check_results_config
        FOREIGN KEY (config_id) REFERENCES health_checks (id)
        ON DELETE CASCADE
);
