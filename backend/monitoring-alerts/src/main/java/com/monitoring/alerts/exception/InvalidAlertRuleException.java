package com.monitoring.alerts.exception;

/**
 * Raised when an alert rule payload is internally inconsistent, for example a metric-threshold
 * rule without an application, or a health-check rule whose target does not reference an owned
 * health check. Mapped to HTTP 400.
 */
public class InvalidAlertRuleException extends RuntimeException {

    public InvalidAlertRuleException(String message) {
        super(message);
    }
}
