package com.monitoring.auth.exception;

/** Thrown when registration uses a username or email that is already taken. */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String message) {
        super(message);
    }
}
