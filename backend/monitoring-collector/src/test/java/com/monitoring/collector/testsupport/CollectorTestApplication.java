package com.monitoring.collector.testsupport;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal application context for the collector module's integration tests.
 *
 * <p>The production entry point lives in {@code monitoring-api}; this class only exists so a
 * module-level test can boot the JPA/collector beans without pulling in every other module.
 * Entity and repository scanning is pointed at {@code com.monitoring} because the test class
 * lives outside the production package root. The schema is created by the real Flyway
 * migrations and only validated by Hibernate.
 */
@SpringBootApplication(scanBasePackages = "com.monitoring")
@EntityScan(basePackages = "com.monitoring")
@EnableJpaRepositories(basePackages = "com.monitoring")
public class CollectorTestApplication {
}
