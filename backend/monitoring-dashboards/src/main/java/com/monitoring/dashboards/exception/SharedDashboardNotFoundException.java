package com.monitoring.dashboards.exception;

/**
 * Raised when a public share token does not resolve to a shared dashboard.
 *
 * <p>The same message is used for an unknown token, a revoked link and a widget that does
 * not belong to the shared dashboard, so the public endpoint does not disclose which part
 * of the link was wrong.
 */
public class SharedDashboardNotFoundException extends RuntimeException {

    public SharedDashboardNotFoundException() {
        super("Shared dashboard was not found or access has been revoked");
    }
}
