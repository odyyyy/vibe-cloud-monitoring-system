package com.monitoring.collector.service;

import com.monitoring.collector.config.UsageProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Drives the usage rollup.
 *
 * <p>Runs hourly so today's running total stays fresh for the Usage page, and re-runs yesterday
 * to finalize it (the previous hour may have been the one that crossed midnight). Like the other
 * maintenance jobs it is guarded by an {@code enabled} flag and swallows failures so a bad run
 * cannot stop the scheduler; the rollup is idempotent, so the next run simply retries.
 */
@Component
@RequiredArgsConstructor
public class UsageRollupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UsageRollupScheduler.class);

    private final UsageRollupService usageRollupService;
    private final UsageProperties usageProperties;

    /** Default: every hour at minute 10. Overridable via {@code monitoring.usage.cron}. */
    @Scheduled(cron = "${monitoring.usage.cron:0 10 * * * *}")
    public void rollupUsage() {
        if (!usageProperties.isEnabled()) {
            log.debug("Usage rollup is disabled (monitoring.usage.enabled=false)");
            return;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        try {
            usageRollupService.rollup(today);
            usageRollupService.rollup(today.minusDays(1));
        } catch (RuntimeException ex) {
            log.error("Usage rollup job failed: {}", ex.getMessage(), ex);
        }
    }
}
