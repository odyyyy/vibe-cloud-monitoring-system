package com.monitoring.healthcheck.service;

import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.repository.HealthCheckConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Runs active health checks that are due according to their interval.
 *
 * <p>Ingestion of a result happens in a short transaction ({@link HealthCheckService#recordResult});
 * the network call itself runs outside any transaction. The scheduler is single-threaded with a
 * fixed delay, so runs never overlap and outbound request volume stays bounded.
 */
@Component
@RequiredArgsConstructor
public class HealthCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckScheduler.class);

    private final HealthCheckConfigRepository configRepository;
    private final HealthCheckService healthCheckService;
    private final HealthCheckExecutor executor;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${monitoring.healthcheck.scheduler-interval-ms:10000}")
    public void runDueChecks() {
        Instant now = clock.instant();
        List<HealthCheckConfig> active = configRepository.findByActiveTrue();
        for (HealthCheckConfig config : active) {
            if (!isDue(config, now)) {
                continue;
            }
            try {
                HealthCheckOutcome outcome = executor.execute(config);
                healthCheckService.recordResult(config.getId(), outcome, clock.instant());
            } catch (RuntimeException ex) {
                log.warn("Health check {} failed to run: {}", config.getId(), ex.getMessage());
            }
        }
    }

    private boolean isDue(HealthCheckConfig config, Instant now) {
        Instant lastCheckedAt = config.getLastCheckedAt();
        return lastCheckedAt == null
                || !lastCheckedAt.plusSeconds(config.getIntervalSeconds()).isAfter(now);
    }
}
