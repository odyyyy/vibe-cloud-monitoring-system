package com.monitoring.healthcheck.exception;

public class HealthCheckAccessDeniedException extends RuntimeException {

    public HealthCheckAccessDeniedException() {
        super("You do not have access to this health check");
    }
}
