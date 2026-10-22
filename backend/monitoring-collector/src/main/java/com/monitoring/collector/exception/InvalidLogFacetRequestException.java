package com.monitoring.collector.exception;

/**
 * The requested facet fields or the requested limit are not supported.
 *
 * <p>The message names the accepted values and never contains internals.
 */
public class InvalidLogFacetRequestException extends RuntimeException {

    public InvalidLogFacetRequestException(String message) {
        super(message);
    }
}
