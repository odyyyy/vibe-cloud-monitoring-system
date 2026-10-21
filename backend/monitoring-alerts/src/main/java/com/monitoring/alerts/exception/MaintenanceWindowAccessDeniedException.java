package com.monitoring.alerts.exception;

public class MaintenanceWindowAccessDeniedException extends RuntimeException {

    public MaintenanceWindowAccessDeniedException() {
        super("You do not have access to this maintenance window");
    }
}
