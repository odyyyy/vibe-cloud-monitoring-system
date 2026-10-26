package com.monitoring.api.exception;

/** A search query exceeded its statement timeout. */
public class SearchTimeoutException extends RuntimeException {

    public SearchTimeoutException() {
        super("Search took too long; try a more specific query");
    }
}
