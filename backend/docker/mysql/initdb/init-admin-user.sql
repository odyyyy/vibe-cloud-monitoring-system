-- Initialize the application user with access from any host (needed on the Docker network).
-- Runs only when the database is initialized for the first time.

-- Create the 'admin' user allowed to connect from any host ('%') if it does not exist yet.
CREATE USER IF NOT EXISTS 'admin'@'%' IDENTIFIED BY 'admin_password';

-- Grant all privileges on the monitoring_db database.
GRANT ALL PRIVILEGES ON monitoring_db.* TO 'admin'@'%';

-- Reload privileges so the changes take effect immediately.
FLUSH PRIVILEGES;