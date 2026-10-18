package com.monitoring.apps.exception;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(Long id) {
        super("Application not found: " + id);
    }
}
