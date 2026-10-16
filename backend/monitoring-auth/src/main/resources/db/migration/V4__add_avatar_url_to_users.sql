-- Добавляем аватар пользователя. Поле необязательное (nullable).
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(512);
