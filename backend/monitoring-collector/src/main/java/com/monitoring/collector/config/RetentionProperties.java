package com.monitoring.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the data-retention job.
 *
 * <p>Retention windows are configurable (for example
 * {@code monitoring.retention.logs-days=14}) and the job can be disabled with
 * {@code monitoring.retention.enabled=false}. The {@code cron} is resolved from
 * {@code monitoring.retention.cron}.
 */
@Component
@ConfigurationProperties(prefix = "monitoring.retention")
@Getter
@Setter
public class RetentionProperties {

    /** When false the retention job does nothing. */
    private boolean enabled = true;

    /** Log events older than this many days are deleted. */
    private int logsDays = 14;

    /** Aggregated metrics older than this many days are deleted. */
    private int metricsDays = 90;

    /** Maximum number of rows deleted per statement (keeps locks short). */
    private int batchSize = 10_000;
}
