package com.monitoring.statuspage.exception;

public class StatusPageAccessDeniedException extends RuntimeException {

    public StatusPageAccessDeniedException() {
        super("You do not have access to this status page");
    }
}
