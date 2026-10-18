package com.monitoring.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for per-application ingestion rate limits.
 *
 * <p>Limits are expressed in requests per minute and are enforced per API key (resolved to the
 * owning application) and per endpoint category, so a metrics-heavy sender cannot starve the
 * logs path. The whole mechanism can be disabled with
 * {@code monitoring.quotas.enabled=false}, which is useful for local development and tests.
 */
@Component
@ConfigurationProperties(prefix = "monitoring.quotas")
@Getter
@Setter
public class IngestionQuotaProperties {

    /** When false no requests are ever rate-limited. */
    private boolean enabled = true;

    /** Maximum metric ingestion requests per minute for a single application. */
    private int metricsPerMinute = 10_000;

    /** Maximum log ingestion requests per minute for a single application. */
    private int logsPerMinute = 5_000;
}
