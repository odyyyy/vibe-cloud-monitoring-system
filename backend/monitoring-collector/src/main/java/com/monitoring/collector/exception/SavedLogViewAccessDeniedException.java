package com.monitoring.collector.exception;

public class SavedLogViewAccessDeniedException extends RuntimeException {

    public SavedLogViewAccessDeniedException() {
        super("You do not have access to this saved view");
    }
}
