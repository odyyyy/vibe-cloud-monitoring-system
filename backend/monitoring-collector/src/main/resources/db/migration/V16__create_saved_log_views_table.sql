-- Сохранённые представления (фильтры) журнала логов. Принадлежат владельцу по owner_id.
-- query_params — канонический JSON-документ с проверенной структурой
-- { applicationId, level?, searchText?, timeRange: { from, to } }. Хранится как TEXT и
-- повторно валидируется только при записи; при чтении интерпретируется как данные.
CREATE TABLE saved_log_views (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    query_params TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_saved_log_views_owner_id (owner_id)
);
