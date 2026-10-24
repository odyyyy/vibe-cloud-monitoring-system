package com.monitoring.alerts.exception;

/** Inconsistent scheduled report payload (recipients, Slack channel or empty sections). */
public class InvalidScheduledReportException extends RuntimeException {

    public InvalidScheduledReportException(String message) {
        super(message);
    }
}
