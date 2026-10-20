package com.monitoring.auth.exception;

/** Raised when a Personal Access Token requests a scope that is not on the allowlist. */
public class InvalidPatScopeException extends RuntimeException {

    public InvalidPatScopeException(String message) {
        super(message);
    }
}
