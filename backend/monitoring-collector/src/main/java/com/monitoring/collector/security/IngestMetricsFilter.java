package com.monitoring.collector.security;

import com.monitoring.apps.entity.Application;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Records ingestion request outcomes for platform self-monitoring.
 *
 * <p>Runs on the dedicated ingestion chain after {@link ApiKeyAuthFilter}, so the authenticated
 * {@link Application} is available. It records a counter tagged with {@code applicationId} and
 * the HTTP {@code status}, plus one aggregated latency timer. No request bodies, headers or
 * telemetry values are recorded.
 */
public class IngestMetricsFilter extends OncePerRequestFilter {

    public static final String REQUESTS_METRIC = "ingest_requests_total";
    public static final String DURATION_METRIC = "ingest_request_duration_seconds";
    public static final String STATUS_TAG = "status";
    public static final String APPLICATION_TAG = "applicationId";

    private final MeterRegistry meterRegistry;

    public IngestMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            filterChain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            record(request, status, System.nanoTime() - startedAt);
        }
    }

    private void record(HttpServletRequest request, int status, long durationNanos) {
        Application application = currentApplication();
        String applicationId = application == null ? "unknown" : String.valueOf(application.getId());

        Counter.builder(REQUESTS_METRIC)
                .description("Ingestion requests by application and HTTP status")
                .tag(APPLICATION_TAG, applicationId)
                .tag(STATUS_TAG, String.valueOf(status))
                .register(meterRegistry)
                .increment();

        Timer.builder(DURATION_METRIC)
                .description("Ingestion request latency")
                .publishPercentiles(0.95)
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private Application currentApplication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Application application) {
            return application;
        }
        return null;
    }
}
