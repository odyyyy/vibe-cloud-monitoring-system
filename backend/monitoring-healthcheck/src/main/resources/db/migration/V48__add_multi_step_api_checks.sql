-- Multi-step API synthetic checks. Single HTTP checks keep their existing definition and are
-- marked with the default check_type; multi-step checks store the workflow as JSON and leave
-- target_url empty. Step diagnostics are recorded next to each result.
ALTER TABLE health_checks
    ADD COLUMN check_type VARCHAR(32) NOT NULL DEFAULT 'SINGLE_HTTP';

ALTER TABLE health_checks
    ADD COLUMN steps_json TEXT NULL;

ALTER TABLE health_checks
    MODIFY COLUMN target_url VARCHAR(2048) NULL;

ALTER TABLE health_check_results
    ADD COLUMN step_results TEXT NULL;
