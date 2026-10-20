package com.monitoring.alerts.exception;

public class AlertIncidentAccessDeniedException extends RuntimeException {

    public AlertIncidentAccessDeniedException() {
        super("You do not have access to this alert incident");
    }
}
