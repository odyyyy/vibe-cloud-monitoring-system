package com.monitoring.retention.service;

import com.monitoring.organization.entity.Organization;
import com.monitoring.organization.repository.OrganizationRepository;
import com.monitoring.retention.config.RetentionProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled entry point of the retention purge.
 *
 * <p>Runs hourly by default ({@code monitoring.retention.cron}) and can be disabled with
 * {@code monitoring.retention.enabled=false}. An in-process {@link AtomicBoolean} guarantees
 * that a slow run is never overlapped by the next tick. Each organization is processed
 * independently, so one tenant's failure does not stop the others; per data type the outcome
 * is recorded by {@link RetentionPurgeService}.
 */
@Component
@RequiredArgsConstructor
public class RetentionPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeJob.class);

    private final RetentionProperties properties;
    private final OrganizationRepository organizationRepository;
    private final RetentionPurgeService purgeService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Default: every hour at minute 0. Overridable via {@code monitoring.retention.cron}. */
    @Scheduled(cron = "${monitoring.retention.cron:0 0 * * * *}")
    public void purgeExpiredData() {
        if (!properties.isEnabled()) {
            log.debug("Data retention is disabled (monitoring.retention.enabled=false)");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("Skipping retention purge: a previous run is still in progress");
            return;
        }
        try {
            Instant now = Instant.now();
            for (Organization organization : organizationRepository.findAll()) {
                try {
                    purgeService.purgeOrganization(organization.getId(), now);
                } catch (RuntimeException ex) {
                    log.error("Retention purge failed for organization {}: {}",
                            organization.getId(), ex.getMessage(), ex);
                }
            }
        } finally {
            running.set(false);
        }
    }
}
