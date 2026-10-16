package com.monitoring.healthcheck.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the health-check scheduler. Disable it with
 * {@code monitoring.healthcheck.scheduler.enabled=false} (for example in tests that should not
 * emit outbound requests).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "monitoring.healthcheck.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class HealthCheckSchedulingConfig {
}
