package com.monitoring.dashboards.exception;

public class WidgetNotFoundException extends RuntimeException {

    public WidgetNotFoundException(Long id) {
        super("Widget " + id + " was not found");
    }
}
