package com.monitoring.healthcheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Module infrastructure for health checks. {@link Clock} is a bean so scheduling and result
 * timestamps are testable.
 */
@Configuration
public class HealthCheckModuleConfig {

    @Bean
    public Clock healthCheckClock() {
        return Clock.systemUTC();
    }
}
