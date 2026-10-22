package com.monitoring.retention.exception;

/** A requested retention value is outside the configured bounds. */
public class InvalidRetentionPolicyException extends RuntimeException {

    public InvalidRetentionPolicyException(String message) {
        super(message);
    }
}
