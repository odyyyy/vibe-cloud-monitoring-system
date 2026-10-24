package com.monitoring.alerts.exception;

public class ScheduledReportAccessDeniedException extends RuntimeException {

    public ScheduledReportAccessDeniedException() {
        super("You do not have access to this scheduled report");
    }
}
