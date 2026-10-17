package com.monitoring.storage.exception;

public class ApplicationAccessDeniedException extends RuntimeException {

    public ApplicationAccessDeniedException() {
        super("Access denied: you do not own this application");
    }
}
