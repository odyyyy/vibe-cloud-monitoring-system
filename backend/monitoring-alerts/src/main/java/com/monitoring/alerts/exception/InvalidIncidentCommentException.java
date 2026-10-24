package com.monitoring.alerts.exception;

/** A comment is empty, too long or otherwise not acceptable (HTTP 400). */
public class InvalidIncidentCommentException extends RuntimeException {

    public InvalidIncidentCommentException(String message) {
        super(message);
    }
}
