package com.monitoring.alerts.entity;

/**
 * Supported delivery mechanisms for alert notifications.
 *
 * <p>{@link #WEBHOOK} posts the platform's JSON payload to a customer HTTPS endpoint,
 * {@link #SLACK} posts a Block Kit message to a Slack incoming webhook, {@link #EMAIL} sends a
 * formatted HTML email over SMTP, {@link #PAGERDUTY} enqueues events in the PagerDuty Events API
 * v2 and {@link #TEAMS} posts a MessageCard to a Microsoft Teams incoming webhook.
 */
public enum NotificationChannelType {
    WEBHOOK,
    SLACK,
    EMAIL,
    PAGERDUTY,
    TEAMS
}
