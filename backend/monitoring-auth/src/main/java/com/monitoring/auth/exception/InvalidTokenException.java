package com.monitoring.auth.exception;

/** Thrown when a confirmation or password-reset token is unknown, expired or already used. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
