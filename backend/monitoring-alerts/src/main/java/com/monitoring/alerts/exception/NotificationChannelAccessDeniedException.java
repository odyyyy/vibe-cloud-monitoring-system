package com.monitoring.alerts.exception;

public class NotificationChannelAccessDeniedException extends RuntimeException {

    public NotificationChannelAccessDeniedException() {
        super("You do not have access to this notification channel");
    }
}
