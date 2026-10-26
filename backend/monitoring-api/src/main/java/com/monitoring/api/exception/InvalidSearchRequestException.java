package com.monitoring.api.exception;

/** The search query, resource type list or per-type limit is invalid. */
public class InvalidSearchRequestException extends RuntimeException {

    public InvalidSearchRequestException(String message) {
        super(message);
    }
}
