package com.monitoring.collector.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled-task infrastructure for the collector maintenance jobs
 * (downsampling and retention).
 *
 * <p>Enabling scheduling itself is harmless when no job is due; each job is individually
 * disabled through its own {@code monitoring.*.enabled} flag.
 */
@Configuration
@EnableScheduling
public class MaintenanceSchedulingConfig {
}
