package com.monitoring.collector.exception;

/**
 * Raised when saving a new view would exceed {@code SavedLogViewService.MAX_SAVED_VIEWS_PER_OWNER}.
 * Mapped to HTTP 409.
 */
public class SavedLogViewLimitExceededException extends RuntimeException {

    public SavedLogViewLimitExceededException(int limit) {
        super("A user cannot have more than " + limit + " saved views");
    }
}
