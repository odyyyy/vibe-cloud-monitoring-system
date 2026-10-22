package com.monitoring.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Limits of the log facet endpoint.
 *
 * <p>Faceting is more expensive than a normal log query: it aggregates over the matching rows
 * instead of reading a single page of them. The limits keep the cost predictable and bounded.
 */
@Component
@ConfigurationProperties(prefix = "monitoring.logs.facets")
@Getter
@Setter
public class LogFacetProperties {

    /**
     * Maximum number of matching rows that are scanned to aggregate the JSON context fields.
     * The most recent rows are used; when the cap is reached the response is marked as
     * approximate.
     */
    private int maxScanRows = 50_000;

    /** Statement timeout of the aggregation queries, in milliseconds. */
    private int timeoutMs = 2_000;

    /** Maximum number of facet requests per organization and window. */
    private int maxRequests = 30;

    /** Length of the rate-limit window, in seconds. */
    private int windowSeconds = 60;
}
