package com.monitoring.alerts.exception;

public class InvalidIncidentStateException extends RuntimeException {

    public InvalidIncidentStateException(String message) {
        super(message);
    }
}
