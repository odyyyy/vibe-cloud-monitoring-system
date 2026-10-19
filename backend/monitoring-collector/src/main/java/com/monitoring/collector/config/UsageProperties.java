package com.monitoring.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for usage tracking and quota visualization.
 *
 * <p>The rollup job can be disabled with {@code monitoring.usage.enabled=false} (useful in
 * tests and single-shot environments). The monthly quotas are the plan limits the Usage page
 * compares against; they are expressed as a number of telemetry items per calendar month.
 */
@Component
@ConfigurationProperties(prefix = "monitoring.usage")
@Getter
@Setter
public class UsageProperties {

    /** When false the usage rollup job does nothing (reads still work). */
    private boolean enabled = true;

    /** Maximum metric points an organization may ingest per calendar month. */
    private long metricsQuota = 10_000_000L;

    /** Maximum log events an organization may ingest per calendar month. */
    private long logsQuota = 5_000_000L;

    /** Maximum trace spans an organization may ingest per calendar month. */
    private long tracesQuota = 1_000_000L;
}
