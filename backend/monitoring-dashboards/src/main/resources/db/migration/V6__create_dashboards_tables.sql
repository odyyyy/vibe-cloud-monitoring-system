-- Дашборды пользователей (control plane). Принадлежат владельцу по owner_id.
CREATE TABLE dashboards (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    owner_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dashboards_owner_id (owner_id)
);

-- Виджеты дашборда. config — JSON-документ (TEXT), интерпретируется только фронтендом.
-- Позиция/размер описаны в сетке из 12 колонок.
CREATE TABLE dashboard_widgets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dashboard_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    config TEXT NOT NULL,
    position_x INT NOT NULL,
    position_y INT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    INDEX idx_dashboard_widgets_dashboard_id (dashboard_id),
    CONSTRAINT fk_dashboard_widgets_dashboard
        FOREIGN KEY (dashboard_id) REFERENCES dashboards (id)
        ON DELETE CASCADE
);
