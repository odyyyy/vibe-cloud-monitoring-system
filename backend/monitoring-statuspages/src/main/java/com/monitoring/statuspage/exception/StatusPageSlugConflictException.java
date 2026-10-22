package com.monitoring.statuspage.exception;

public class StatusPageSlugConflictException extends RuntimeException {

    public StatusPageSlugConflictException() {
        super("This slug is already in use");
    }
}
