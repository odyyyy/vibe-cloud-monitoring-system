package com.monitoring.collector.security;

import com.monitoring.collector.config.IngestionQuotaProperties;
import com.monitoring.collector.controller.LogIngestController;
import com.monitoring.collector.controller.MetricIngestController;
import com.monitoring.collector.service.LogIngestService;
import com.monitoring.collector.service.MetricIngestService;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.repository.ApplicationRepository;
import com.monitoring.storage.security.ApiKeyHasher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the ingestion security chain end to end at the web layer with deliberately tiny
 * quotas, without booting the JPA/Flyway stack. The API-key filter resolves the application,
 * the rate-limit filter enforces the per-application quota, and the controller is only reached
 * when the request is within the limit.
 */
@SpringBootTest(
        classes = RateLimitWebTest.SliceApplication.class,
        properties = {
                "monitoring.quotas.metrics-per-minute=2",
                "monitoring.quotas.logs-per-minute=3"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitWebTest {

    private static final String METRICS_BODY = "{\"metrics\":[{\"name\":\"cpu_usage\",\"value\":75.5,"
            + "\"timestamp\":\"2026-10-09T10:00:00Z\"}]}";

    private static final String LOGS_BODY = "{\"events\":[{\"level\":\"INFO\",\"message\":\"hello\","
            + "\"timestamp\":\"2026-10-09T10:00:00Z\"}]}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private ApplicationRepository applicationRepository;

    @MockBean
    private MetricIngestService metricIngestService;

    @MockBean
    private LogIngestService logIngestService;

    @Test
    void withinLimit_returns202() throws Exception {
        stubApplication("app_rate_a", application(7L, "app_rate_a"));

        ingestMetrics("app_rate_a").andExpect(status().isAccepted());
        ingestMetrics("app_rate_a").andExpect(status().isAccepted());
    }

    @Test
    void beyondLimit_returns429WithRetryAfterAndBody() throws Exception {
        stubApplication("app_rate_a", application(7L, "app_rate_a"));

        ingestMetrics("app_rate_a").andExpect(status().isAccepted());
        ingestMetrics("app_rate_a").andExpect(status().isAccepted());

        ingestMetrics("app_rate_a")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.retry_after_seconds").isNumber());

        double rejected = meterRegistry.get(IngestionRateLimiter.RATE_LIMITED_METRIC)
                .tag(IngestionRateLimiter.APPLICATION_ID_TAG, "7").counter().count();
        org.junit.jupiter.api.Assertions.assertTrue(rejected >= 1.0,
                "rate-limited metric must be recorded for the application");
    }

    @Test
    void otherApiKey_isNotAffected() throws Exception {
        stubApplication("app_rate_a", application(7L, "app_rate_a"));
        stubApplication("app_rate_b", application(8L, "app_rate_b"));

        ingestMetrics("app_rate_a").andExpect(status().isAccepted());
        ingestMetrics("app_rate_a").andExpect(status().isAccepted());
        ingestMetrics("app_rate_a").andExpect(status().isTooManyRequests());

        ingestMetrics("app_rate_b").andExpect(status().isAccepted());
    }

    @Test
    void logsHaveSeparateLimitFromMetrics() throws Exception {
        stubApplication("app_rate_a", application(7L, "app_rate_a"));

        ingestMetrics("app_rate_a").andExpect(status().isAccepted());
        ingestMetrics("app_rate_a").andExpect(status().isAccepted());
        ingestMetrics("app_rate_a").andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/v1/ingest/logs")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, "app_rate_a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGS_BODY))
                .andExpect(status().isAccepted());
    }

    private static Application application(long id, String name) {
        return Application.builder().id(id).name(name).apiKeyHash(ApiKeyHasher.hash(name)).build();
    }

    private void stubApplication(String rawKey, Application application) {
        when(applicationRepository.findByApiKeyHash(ApiKeyHasher.hash(rawKey)))
                .thenReturn(Optional.of(application));
    }

    private ResultActions ingestMetrics(String apiKey) throws Exception {
        return mockMvc.perform(post("/api/v1/ingest/metrics")
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(METRICS_BODY));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @Import({MetricIngestController.class, LogIngestController.class, IngestSecurityConfig.class,
            IngestionRateLimiter.class, IngestionQuotaProperties.class, MetricsConfig.class})
    static class SliceApplication {
    }

    @TestConfiguration
    static class MetricsConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
