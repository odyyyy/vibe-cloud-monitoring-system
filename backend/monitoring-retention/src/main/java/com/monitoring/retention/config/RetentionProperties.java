package com.monitoring.retention.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for per-organization telemetry retention and the purge job.
 *
 * <p>Defaults are used until an organization stores its own policy. The per-type maximum is a
 * hard upper bound enforced by the API so a tenant cannot keep data indefinitely. The job
 * deletes in bounded batches; both the batch size and the number of batches per organization
 * and data type are capped so a single run cannot lock a large part of a table.
 */
@Component
@ConfigurationProperties(prefix = "monitoring.retention")
@Getter
@Setter
public class RetentionProperties {

    /** When false the scheduled purge does nothing. */
    private boolean enabled = true;

    /** Default logs retention in days when an organization has no explicit policy. */
    private int defaultLogsDays = 14;

    /** Default metrics retention in days when an organization has no explicit policy. */
    private int defaultMetricsDays = 30;

    /** Default traces retention in days when an organization has no explicit policy. */
    private int defaultTracesDays = 7;

    /** Hard maximum logs retention in days. */
    private int maxLogsDays = 90;

    /** Hard maximum metrics retention in days. */
    private int maxMetricsDays = 365;

    /** Hard maximum traces retention in days. */
    private int maxTracesDays = 30;

    /** Maximum number of ids deleted by a single statement. */
    private int batchSize = 5_000;

    /** Maximum number of batches processed per organization and data type per run. */
    private int maxBatchesPerRun = 100;
}
