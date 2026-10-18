package com.monitoring.collector.exception;

public class SavedLogViewNotFoundException extends RuntimeException {

    public SavedLogViewNotFoundException(Long id) {
        super("Saved view " + id + " was not found");
    }
}
