package com.monitoring.alerts.exception;

/** The organization already has the maximum number of scheduled reports. */
public class ScheduledReportLimitExceededException extends RuntimeException {

    public ScheduledReportLimitExceededException(int limit) {
        super("An organization can configure at most " + limit + " scheduled reports");
    }
}
