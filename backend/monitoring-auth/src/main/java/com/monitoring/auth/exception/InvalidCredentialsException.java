package com.monitoring.auth.exception;

/**
 * Raised for both an unknown username and a wrong password.
 *
 * <p>A single exception and a single HTTP 401 response are used deliberately so the API does not
 * reveal whether an account exists (user-enumeration protection).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
