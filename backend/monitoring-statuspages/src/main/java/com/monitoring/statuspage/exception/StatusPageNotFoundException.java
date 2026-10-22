package com.monitoring.statuspage.exception;

public class StatusPageNotFoundException extends RuntimeException {

    public StatusPageNotFoundException() {
        super("Status page was not found");
    }
}
