package com.monitoring.alerts.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the alert evaluation scheduler. Disable it with
 * {@code monitoring.alerts.scheduler.enabled=false} (for example in tests that should not run
 * rule evaluations automatically).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "monitoring.alerts.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AlertSchedulingConfig {
}
