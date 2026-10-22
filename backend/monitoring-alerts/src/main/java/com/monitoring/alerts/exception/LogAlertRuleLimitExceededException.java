package com.monitoring.alerts.exception;

/** The organization reached the active log-alert rule quota (HTTP 409). */
public class LogAlertRuleLimitExceededException extends RuntimeException {

    public LogAlertRuleLimitExceededException(int limit) {
        super("At most " + limit + " active log alert rules are allowed per organization");
    }
}
