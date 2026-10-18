package com.monitoring.api;

import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.apps.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the wiring of the Spring Security chains in the real application context:
 * ingestion is protected by X-API-Key (no JWT) and a valid key takes the request all the
 * way to persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IngestIntegrationTest {

    private static final String VALID_BODY = "{\"metrics\":[{\"name\":\"cpu_usage\",\"value\":75.5,"
            + "\"timestamp\":\"2026-10-09T10:00:00Z\",\"tags\":{\"host\":\"server-1\"}}]}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MetricSampleRepository metricSampleRepository;

    @BeforeEach
    void setUp() {
        metricSampleRepository.deleteAll();
        applicationRepository.deleteAll();
        applicationRepository.save(Application.builder()
                .name("integration-app")
                .apiKeyHash(ApiKeyHasher.hash("app_integration_test"))
                .organizationId(1L)
                .build());
    }

    @Test
    void ingest_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingest_withInvalidApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/metrics")
                        .header("X-API-Key", "app_does_not_exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingest_withValidApiKey_persistsAndReturns202() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/metrics")
                        .header("X-API-Key", "app_integration_test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted());

        assertEquals(1, metricSampleRepository.count());
    }

    @Test
    void ingest_withValidApiKeyButInvalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/metrics")
                        .header("X-API-Key", "app_integration_test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metrics\":[]}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, metricSampleRepository.count());
    }
}
