package com.monitoring.api;

import com.monitoring.healthcheck.dto.CreateHealthCheckRequest;
import com.monitoring.healthcheck.dto.HealthCheckResponse;
import com.monitoring.healthcheck.dto.HealthCheckResultResponse;
import com.monitoring.healthcheck.dto.UpdateHealthCheckRequest;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.exception.HealthCheckAccessDeniedException;
import com.monitoring.healthcheck.exception.HealthCheckNotFoundException;
import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
import com.monitoring.healthcheck.repository.HealthCheckConfigRepository;
import com.monitoring.healthcheck.repository.HealthCheckResultRepository;
import com.monitoring.healthcheck.service.HealthCheckOutcome;
import com.monitoring.healthcheck.service.HealthCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies health-check persistence, result history ordering, tenant isolation and SSRF
 * validation against a real persistence context. The scheduler is disabled so no outbound
 * requests are made.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:healthchecks-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "monitoring.healthcheck.scheduler.enabled=false"
})
class HealthChecksIntegrationTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final String PUBLIC_URL = "http://8.8.8.8/health";

    @Autowired
    private HealthCheckService healthCheckService;

    @Autowired
    private HealthCheckConfigRepository configRepository;

    @Autowired
    private HealthCheckResultRepository resultRepository;

    @BeforeEach
    void setUp() {
        resultRepository.deleteAll();
        configRepository.deleteAll();
    }

    private HealthCheckResponse create() {
        return healthCheckService.create(OWNER_ID, CreateHealthCheckRequest.builder()
                .name("API health")
                .targetUrl(PUBLIC_URL)
                .method(HealthCheckMethod.GET)
                .expectedStatusCode(200)
                .intervalSeconds(30)
                .active(true)
                .build());
    }

    @Test
    void createAppliesDefaults() {
        HealthCheckResponse created = healthCheckService.create(OWNER_ID, CreateHealthCheckRequest.builder()
                .name("API health")
                .targetUrl(PUBLIC_URL)
                .method(HealthCheckMethod.GET)
                .build());

        assertEquals(200, created.getExpectedStatusCode());
        assertEquals(HealthCheckService.DEFAULT_INTERVAL_SECONDS, created.getIntervalSeconds());
        assertTrue(created.isActive());
        assertEquals(OWNER_ID, created.getOrganizationId());
    }

    @Test
    void createListAndUpdate() {
        HealthCheckResponse created = create();

        List<HealthCheckResponse> list = healthCheckService.list(OWNER_ID);
        assertEquals(1, list.size());

        HealthCheckResponse updated = healthCheckService.update(OWNER_ID, created.getId(),
                UpdateHealthCheckRequest.builder().active(false).name("Renamed").build());
        assertEquals("Renamed", updated.getName());
        assertFalse(updated.isActive());
        assertEquals(PUBLIC_URL, updated.getTargetUrl());
    }

    @Test
    void recordResultStoresHistoryNewestFirst() {
        HealthCheckResponse created = create();

        healthCheckService.recordResult(created.getId(),
                new HealthCheckOutcome(HealthStatus.UP, 120, null),
                Instant.parse("2026-10-16T10:00:00Z"));
        healthCheckService.recordResult(created.getId(),
                new HealthCheckOutcome(HealthStatus.DOWN, 50, "Expected status 200 but received 500"),
                Instant.parse("2026-10-16T10:05:00Z"));

        List<HealthCheckResultResponse> results = healthCheckService.listResults(OWNER_ID, created.getId(), null);
        assertEquals(2, results.size());
        assertEquals(HealthStatus.DOWN, results.get(0).getStatus());
        assertEquals(HealthStatus.UP, results.get(1).getStatus());
        assertEquals(created.getId(), results.get(0).getConfigId());
    }

    @Test
    void listResultsClampsLimit() {
        HealthCheckResponse created = create();
        for (int i = 0; i < 5; i++) {
            healthCheckService.recordResult(created.getId(),
                    new HealthCheckOutcome(HealthStatus.UP, 10, null),
                    Instant.parse("2026-10-16T10:0" + i + ":00Z"));
        }

        assertEquals(2, healthCheckService.listResults(OWNER_ID, created.getId(), 2).size());
        assertEquals(5, healthCheckService.listResults(OWNER_ID, created.getId(), 10_000).size());
    }

    @Test
    void deleteRemovesConfigAndItsResults() {
        HealthCheckResponse created = create();
        healthCheckService.recordResult(created.getId(),
                new HealthCheckOutcome(HealthStatus.UP, 10, null),
                Instant.parse("2026-10-16T10:00:00Z"));

        healthCheckService.delete(OWNER_ID, created.getId());

        assertEquals(0, configRepository.count());
        assertEquals(0, resultRepository.count());
    }

    @Test
    void anotherUserCannotAccessChecks() {
        HealthCheckResponse created = create();

        assertEquals(0, healthCheckService.list(OTHER_ID).size());
        assertThrows(HealthCheckAccessDeniedException.class,
                () -> healthCheckService.update(OTHER_ID, created.getId(),
                        UpdateHealthCheckRequest.builder().name("x").build()));
        assertThrows(HealthCheckAccessDeniedException.class,
                () -> healthCheckService.listResults(OTHER_ID, created.getId(), 10));
        assertThrows(HealthCheckAccessDeniedException.class,
                () -> healthCheckService.delete(OTHER_ID, created.getId()));
    }

    @Test
    void missingCheckRaisesNotFound() {
        assertThrows(HealthCheckNotFoundException.class,
                () -> healthCheckService.listResults(OWNER_ID, 999L, 10));
    }

    @Test
    void ssrfTargetsAreRejected() {
        assertThrows(InvalidTargetUrlException.class,
                () -> healthCheckService.create(OWNER_ID, CreateHealthCheckRequest.builder()
                        .name("metadata")
                        .targetUrl("http://169.254.169.254/latest/meta-data/")
                        .method(HealthCheckMethod.GET)
                        .build()));
        assertEquals(0, configRepository.count());
    }
}
