package com.monitoring.collector.service;

import com.monitoring.collector.config.DownsamplingProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Schedules the collector maintenance jobs.
 *
 * <p>Each job is guarded by its own {@code enabled} flag so it can be turned off for local
 * development. Failures are caught and logged (never propagated) so a single bad run cannot
 * stop the scheduler; the next run retries and the jobs are idempotent.
 */
@Component
@RequiredArgsConstructor
public class MetricMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MetricMaintenanceScheduler.class);

    private final MetricMaintenanceService maintenanceService;
    private final DownsamplingProperties downsamplingProperties;

    /** Default: every hour at minute 5. Overridable via {@code monitoring.downsample.cron}. */
    @Scheduled(cron = "${monitoring.downsample.cron:0 5 * * * *}")
    public void downsampleRawMetrics() {
        if (!downsamplingProperties.isEnabled()) {
            log.debug("Metric downsampling is disabled (monitoring.downsample.enabled=false)");
            return;
        }
        try {
            maintenanceService.downsample(Instant.now(),
                    downsamplingProperties.getRawRetentionHours(),
                    downsamplingProperties.getBatchSize());
        } catch (RuntimeException ex) {
            log.error("Metric downsampling job failed: {}", ex.getMessage(), ex);
        }
    }
}
