package com.monitoring.dashboards.exception;

/**
 * Raised when adding a widget would exceed {@code DashboardService.MAX_WIDGETS_PER_DASHBOARD}.
 * Mapped to HTTP 409.
 */
public class WidgetLimitExceededException extends RuntimeException {

    public WidgetLimitExceededException(int limit) {
        super("A dashboard cannot have more than " + limit + " widgets");
    }
}
