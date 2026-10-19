package com.monitoring.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled-task infrastructure for the login brute-force guard's periodic
 * eviction of stale attempt buckets. Enabling scheduling is harmless when no task is due, and
 * the guard prunes on write as well, so it works even when this config is absent.
 */
@Configuration
@EnableScheduling
public class AuthSchedulingConfig {
}
