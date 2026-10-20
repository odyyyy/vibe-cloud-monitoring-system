package com.monitoring.auth.exception;

/** Raised when a token to revoke does not exist or is not owned by the caller. */
public class PatNotFoundException extends RuntimeException {

    public PatNotFoundException() {
        super("Token not found");
    }
}
