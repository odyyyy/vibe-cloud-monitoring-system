package com.monitoring.auth.exception;

/** Raised when the authenticated user id no longer resolves to an account. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("User not found");
    }
}
