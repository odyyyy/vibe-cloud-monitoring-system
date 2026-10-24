package com.monitoring.alerts.service;

/**
 * Result of a single delivery attempt.
 *
 * <p>{@code retryable} is true only for network errors, HTTP 429 and HTTP 5xx responses. Other
 * 4xx responses indicate a permanent configuration problem and are not retried.
 */
public record NotificationSendResult(
        boolean success,
        Integer httpStatus,
        boolean retryable,
        String error) {

    public static NotificationSendResult success(int httpStatus) {
        return new NotificationSendResult(true, httpStatus, false, null);
    }

    public static NotificationSendResult delivered() {
        return new NotificationSendResult(true, null, false, null);
    }

    public static NotificationSendResult httpFailure(int httpStatus, String error) {
        boolean retryable = httpStatus == 429 || httpStatus >= 500;
        return new NotificationSendResult(false, httpStatus, retryable, error);
    }

    public static NotificationSendResult networkFailure(String error) {
        return new NotificationSendResult(false, null, true, error);
    }

    public static NotificationSendResult permanentFailure(String error) {
        return new NotificationSendResult(false, null, false, error);
    }
}
