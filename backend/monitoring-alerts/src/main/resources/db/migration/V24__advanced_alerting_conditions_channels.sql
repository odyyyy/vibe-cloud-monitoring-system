-- Advanced alerting: compound conditions (AND/OR) and Slack/Email notification channels.
-- Existing single-condition rules keep working: when alert_rules has no rows in
-- alert_rule_conditions the evaluator falls back to the legacy target/condition/threshold columns.

-- How multiple conditions are combined: 'AND' (all must breach) or 'OR' (any must breach).
ALTER TABLE alert_rules ADD COLUMN logic_operator VARCHAR(4) NOT NULL DEFAULT 'AND';

-- Ordered list of conditions belonging to a rule. Populated for compound rules; empty for
-- legacy single-condition rules (the evaluator then uses alert_rules.*_value columns).
CREATE TABLE alert_rule_conditions (
    rule_id BIGINT NOT NULL,
    condition_order INT NOT NULL,
    target VARCHAR(255) NOT NULL,
    operator VARCHAR(16) NOT NULL,
    threshold_value DOUBLE NOT NULL,
    PRIMARY KEY (rule_id, condition_order),
    CONSTRAINT fk_alert_rule_conditions_rule
        FOREIGN KEY (rule_id) REFERENCES alert_rules (id)
        ON DELETE CASCADE
);

-- Notification channels now support WEBHOOK, SLACK and EMAIL. Each type uses exactly one
-- destination: url for WEBHOOK, slack_webhook_url for SLACK, email_address for EMAIL.
-- The legacy url column becomes optional so Slack/Email channels can leave it unset.
ALTER TABLE notification_channels MODIFY COLUMN url VARCHAR(2048) NULL;
ALTER TABLE notification_channels ADD COLUMN slack_webhook_url VARCHAR(2048) NULL;
ALTER TABLE notification_channels ADD COLUMN email_address VARCHAR(320) NULL;
