package com.monitoring.healthcheck.exception;

public class HealthCheckNotFoundException extends RuntimeException {

    public HealthCheckNotFoundException(Long id) {
        super("Health check " + id + " was not found");
    }
}
