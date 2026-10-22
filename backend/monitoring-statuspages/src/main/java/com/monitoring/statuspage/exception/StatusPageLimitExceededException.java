package com.monitoring.statuspage.exception;

public class StatusPageLimitExceededException extends RuntimeException {

    public StatusPageLimitExceededException(int limit) {
        super("An organization cannot have more than " + limit + " status pages");
    }
}
