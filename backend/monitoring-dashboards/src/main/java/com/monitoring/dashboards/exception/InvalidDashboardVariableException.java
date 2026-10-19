package com.monitoring.dashboards.exception;

/**
 * Raised when dashboard template variable definitions are inconsistent (duplicate names, a
 * default value outside the declared options, or too many variables). Mapped to HTTP 400:
 * definitions are client-supplied data and are never interpreted as code.
 */
public class InvalidDashboardVariableException extends RuntimeException {

    public InvalidDashboardVariableException(String message) {
        super(message);
    }
}
