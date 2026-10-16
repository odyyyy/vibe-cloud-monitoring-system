package com.monitoring.dashboards.exception;

/**
 * Raised when a widget config is not a valid JSON object. Mapped to HTTP 400: config is a
 * client-supplied document and must never be interpreted as code.
 */
public class InvalidWidgetConfigException extends RuntimeException {

    public InvalidWidgetConfigException(String message) {
        super(message);
    }
}
