package com.monitoring.alerts.exception;

/**
 * A notification channel payload is inconsistent or its destination has an invalid format
 * (for example a Slack URL that is not a Slack host, or a malformed email address).
 */
public class InvalidNotificationChannelException extends RuntimeException {

    public InvalidNotificationChannelException(String message) {
        super(message);
    }
}
