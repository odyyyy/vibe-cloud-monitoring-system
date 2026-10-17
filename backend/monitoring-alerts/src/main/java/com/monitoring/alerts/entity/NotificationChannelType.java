package com.monitoring.alerts.entity;

/**
 * Supported delivery mechanisms for alert notifications.
 *
 * <p>Only {@link #WEBHOOK} exists in the MVP; email/Slack integrations are out of scope.
 */
public enum NotificationChannelType {
    WEBHOOK
}
