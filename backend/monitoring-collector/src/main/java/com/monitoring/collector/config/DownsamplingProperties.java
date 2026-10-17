package com.monitoring.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the metric downsampling job.
 *
 * <p>The job can be disabled entirely with {@code monitoring.downsample.enabled=false}
 * (useful for local development); the {@code cron} is resolved from
 * {@code monitoring.downsample.cron}.
 */
@Component
@ConfigurationProperties(prefix = "monitoring.downsample")
@Getter
@Setter
public class DownsamplingProperties {

    /** When false the downsampling job does nothing. */
    private boolean enabled = true;

    /** Raw samples older than this many hours are aggregated and then deleted. */
    private int rawRetentionHours = 24;

    /** Maximum number of raw rows deleted per statement (keeps locks short). */
    private int batchSize = 10_000;
}
