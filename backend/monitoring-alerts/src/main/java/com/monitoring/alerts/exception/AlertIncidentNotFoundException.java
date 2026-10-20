package com.monitoring.alerts.exception;

public class AlertIncidentNotFoundException extends RuntimeException {

    public AlertIncidentNotFoundException(Long id) {
        super("Alert incident " + id + " was not found");
    }
}
