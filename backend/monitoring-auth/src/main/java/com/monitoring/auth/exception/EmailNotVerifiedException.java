package com.monitoring.auth.exception;

/** Thrown when a user with valid credentials tries to sign in before confirming their email. */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
