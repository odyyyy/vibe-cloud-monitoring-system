package com.monitoring.collector.dto.response;

/**
 * One value of a facet with the number of matching log events.
 *
 * @param value the value as it appears in the log data
 * @param count how many matching log events carry this value
 */
public record LogFacetValueResponse(String value, long count) {
}
