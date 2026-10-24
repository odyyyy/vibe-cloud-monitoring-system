package com.monitoring.alerts.exception;

/** The organization already has the maximum number of notification channels. */
public class NotificationChannelLimitExceededException extends RuntimeException {

    public NotificationChannelLimitExceededException(int limit) {
        super("An organization can configure at most " + limit + " notification channels");
    }
}
