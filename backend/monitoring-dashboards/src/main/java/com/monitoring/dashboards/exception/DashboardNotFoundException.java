package com.monitoring.dashboards.exception;

public class DashboardNotFoundException extends RuntimeException {

    public DashboardNotFoundException(Long id) {
        super("Dashboard " + id + " was not found");
    }

    public DashboardNotFoundException(String message) {
        super(message);
    }
}
