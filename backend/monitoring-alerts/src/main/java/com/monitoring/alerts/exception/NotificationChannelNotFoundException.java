package com.monitoring.alerts.exception;

public class NotificationChannelNotFoundException extends RuntimeException {

    public NotificationChannelNotFoundException(Long id) {
        super("Notification channel " + id + " was not found");
    }
}
