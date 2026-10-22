package com.monitoring.collector.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled-task infrastructure for the collector maintenance job
 * (metric downsampling). The per-organization retention purge ships with its own scheduling
 * in {@code monitoring-retention}.
 *
 * <p>Enabling scheduling itself is harmless when no job is due; each job is individually
 * disabled through its own {@code monitoring.*.enabled} flag.
 */
@Configuration
@EnableScheduling
public class MaintenanceSchedulingConfig {
}
