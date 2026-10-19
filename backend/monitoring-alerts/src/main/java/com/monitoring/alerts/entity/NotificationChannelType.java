package com.monitoring.alerts.entity;

/**
 * Supported delivery mechanisms for alert notifications.
 *
 * <p>{@link #WEBHOOK} posts the platform's JSON payload to a customer HTTPS endpoint,
 * {@link #SLACK} posts a Block Kit message to a Slack incoming webhook, and {@link #EMAIL} sends
 * a formatted HTML email over SMTP. Slack and Email are native integrations added on top of the
 * generic webhook.
 */
public enum NotificationChannelType {
    WEBHOOK,
    SLACK,
    EMAIL
}
