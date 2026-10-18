package com.monitoring.apps.exception;

/**
 * Raised when an application name is already used by another application of the same owner.
 * Mapped to HTTP 409.
 */
public class ApplicationNameConflictException extends RuntimeException {

    public ApplicationNameConflictException(String name) {
        super("An application named '" + name + "' already exists");
    }
}
