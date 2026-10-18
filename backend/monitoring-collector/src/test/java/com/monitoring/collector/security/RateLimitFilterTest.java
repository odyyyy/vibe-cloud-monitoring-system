package com.monitoring.collector.security;

import com.monitoring.collector.config.IngestionQuotaProperties;
import com.monitoring.storage.entity.Application;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private static final Instant NOW = Instant.ofEpochSecond(100 * 60 + 10);

    private MeterRegistry registry;
    private IngestionQuotaProperties properties;
    private IngestionRateLimiter limiter;
    private RateLimitFilter filter;
    private Application application;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);

        properties = new IngestionQuotaProperties();
        properties.setMetricsPerMinute(2);
        properties.setLogsPerMinute(5);

        limiter = new IngestionRateLimiter(provider);
        filter = new RateLimitFilter(limiter, properties) {
            @Override
            Instant now() {
                return NOW;
            }
        };

        application = Application.builder()
                .id(7L).name("my-app").apiKeyHash("hash").build();
        authenticate(application);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Application app) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                app, null, List.of(new SimpleGrantedAuthority("ROLE_AGENT"))));
    }

    private MockHttpServletResponse perform(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.addHeader(RateLimitFilter.API_KEY_HEADER, "app_plaintext_key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void withinLimit_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ingest/metrics");
        request.addHeader(RateLimitFilter.API_KEY_HEADER, "app_plaintext_key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest(), "chain must be called when under the limit");
        assertEquals(200, response.getStatus());
    }

    @Test
    void exceedingLimit_returns429WithRetryAfterAndJsonBody() throws Exception {
        assertEquals(200, perform("/api/v1/ingest/metrics").getStatus());
        assertEquals(200, perform("/api/v1/ingest/metrics").getStatus());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ingest/metrics");
        request.addHeader(RateLimitFilter.API_KEY_HEADER, "app_plaintext_key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertNull(chain.getRequest(), "chain must not be called when over the limit");
        assertEquals("50", response.getHeader(HttpHeaders.RETRY_AFTER));
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("{\"error\":\"Rate limit exceeded\",\"retry_after_seconds\":50}",
                response.getContentAsString());
    }

    @Test
    void otherApplication_isNotAffected() throws Exception {
        perform("/api/v1/ingest/metrics");
        perform("/api/v1/ingest/metrics");
        assertEquals(429, perform("/api/v1/ingest/metrics").getStatus());

        Application other = Application.builder()
                .id(99L).name("other-app").apiKeyHash("hash2").build();
        authenticate(other);

        assertEquals(200, perform("/api/v1/ingest/metrics").getStatus());
    }

    @Test
    void metricsAndLogsHaveIndependentLimits() throws Exception {
        perform("/api/v1/ingest/metrics");
        perform("/api/v1/ingest/metrics");
        assertEquals(429, perform("/api/v1/ingest/metrics").getStatus());

        // The logs endpoint has its own budget for the same application.
        assertEquals(200, perform("/api/v1/ingest/logs").getStatus());
    }

    @Test
    void whenDisabled_neverLimits() throws Exception {
        properties.setEnabled(false);

        for (int i = 0; i < 10; i++) {
            assertEquals(200, perform("/api/v1/ingest/metrics").getStatus());
        }
    }

    @Test
    void unknownIngestPath_isNotLimited() throws Exception {
        assertEquals(200, perform("/api/v1/ingest/unknown").getStatus());
        assertEquals(200, perform("/api/v1/ingest/unknown").getStatus());
    }

    @Test
    void rejectionIncrementsMetricTaggedByApplicationId() throws Exception {
        perform("/api/v1/ingest/metrics");
        perform("/api/v1/ingest/metrics");
        perform("/api/v1/ingest/metrics");

        assertEquals(1.0, registry.get(IngestionRateLimiter.RATE_LIMITED_METRIC)
                .tag(IngestionRateLimiter.APPLICATION_ID_TAG, "7").counter().count());
    }

    @Test
    void withoutAuthenticatedApplication_passesThrough() throws Exception {
        SecurityContextHolder.clearContext();

        assertEquals(200, perform("/api/v1/ingest/metrics").getStatus());
    }
}
