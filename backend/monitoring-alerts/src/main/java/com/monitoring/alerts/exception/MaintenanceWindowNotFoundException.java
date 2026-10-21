package com.monitoring.alerts.exception;

public class MaintenanceWindowNotFoundException extends RuntimeException {

    public MaintenanceWindowNotFoundException(Long id) {
        super("Maintenance window " + id + " was not found");
    }
}
