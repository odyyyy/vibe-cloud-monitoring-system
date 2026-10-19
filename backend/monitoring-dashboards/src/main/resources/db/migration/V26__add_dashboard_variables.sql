-- Template variable definitions для дашборда (Grafana-style). Хранятся как JSON-массив
-- {name, type, options, defaultValue}; интерпретируются только фронтендом.
-- Выбранные пользователем значения не персистятся (клиентское состояние).
ALTER TABLE dashboards ADD COLUMN variables TEXT NULL;
