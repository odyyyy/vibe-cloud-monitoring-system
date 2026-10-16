-- Добавляем аватар пользователя. Поле необязательное (nullable).
-- В текущей конфигурации схема управляется Hibernate (ddl-auto=update), поэтому
-- миграция приведена для согласованности с V2/V3 и на случай подключения Flyway.
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(512);
