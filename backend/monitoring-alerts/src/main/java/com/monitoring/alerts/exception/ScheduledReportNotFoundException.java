package com.monitoring.alerts.exception;

public class ScheduledReportNotFoundException extends RuntimeException {

    public ScheduledReportNotFoundException(Long id) {
        super("Scheduled report " + id + " was not found");
    }
}
