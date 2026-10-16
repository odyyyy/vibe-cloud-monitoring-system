package com.monitoring.dashboards.exception;

public class DashboardAccessDeniedException extends RuntimeException {

    public DashboardAccessDeniedException() {
        super("You do not have access to this dashboard");
    }
}
