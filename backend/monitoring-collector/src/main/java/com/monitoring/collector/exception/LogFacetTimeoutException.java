package com.monitoring.collector.exception;

/**
 * The facet aggregation did not finish within the statement timeout.
 *
 * <p>The log result list is unaffected: the explorer keeps working without facets.
 */
public class LogFacetTimeoutException extends RuntimeException {

    public LogFacetTimeoutException() {
        super("The facet aggregation timed out; narrow the time range and try again");
    }
}
