-- Агрегированные (downsampled) метрики.
-- Сырые точки старше окна downsampling сворачиваются в часовые корзины
-- (COUNT/AVG/MAX/MIN) и затем удаляются; эта таблица хранит результат.
-- tenant-скоуп через application_id.
-- bucket_epoch — начало временной корзины в epoch-секундах (UTC); целое число,
-- чтобы не зависеть от таймзоны БД и совпадать с FLOOR(UNIX_TIMESTAMP(event_time)).
-- tag_signature — детерминированная (отсортированная по ключу) склейка меток,
-- обрезанная до 500 символов; используется как ключ серии и часть UNIQUE-ключа,
-- который делает повторный запуск downsampling идемпотентным.
CREATE TABLE metric_aggregates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    granularity VARCHAR(16) NOT NULL,
    bucket_epoch BIGINT NOT NULL,
    tag_signature VARCHAR(500) NOT NULL DEFAULT '',
    sample_count BIGINT NOT NULL,
    value_avg DOUBLE NOT NULL,
    value_max DOUBLE NOT NULL,
    value_min DOUBLE NOT NULL,
    CONSTRAINT fk_metric_aggregates_application
        FOREIGN KEY (application_id) REFERENCES applications (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_metric_aggregates_series
        UNIQUE (application_id, name, granularity, bucket_epoch, tag_signature)
);

-- Ускоряет retention: удаление агрегатов старше N дней по bucket_epoch.
CREATE INDEX idx_metric_aggregates_bucket_epoch ON metric_aggregates (bucket_epoch);
