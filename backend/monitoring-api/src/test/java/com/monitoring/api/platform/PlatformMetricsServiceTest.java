package com.monitoring.api.platform;

import com.monitoring.apps.entity.Application;
import com.monitoring.collector.dto.request.MetricIngestRequest;
import com.monitoring.collector.dto.request.MetricPoint;
import com.monitoring.collector.service.MetricIngestService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.monitoring.api.platform.PlatformResourceService.INGEST_LATENCY_METRIC;
import static com.monitoring.api.platform.PlatformResourceService.INGEST_REQUESTS_METRIC;
import static com.monitoring.api.platform.PlatformResourceService.JVM_HEAP_METRIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformMetricsServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private MetricIngestService metricIngestService;
    private PlatformResourceService platformResourceService;
    private PlatformMetricsService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricIngestService = mock(MetricIngestService.class);
        platformResourceService = mock(PlatformResourceService.class);
        service = new PlatformMetricsService(meterRegistry, metricIngestService, platformResourceService);
    }

    @Test
    void collectExportsHeapAndIngestionDeltasWithoutSensitiveTags() {
        Counter counter = Counter.builder(INGEST_REQUESTS_METRIC)
                .tags("applicationId", "42", "status", "200")
                .register(meterRegistry);
        counter.increment(3);

        List<MetricPoint> first = service.collect();

        assertTrue(point(first, JVM_HEAP_METRIC).isPresent());
        MetricPoint rate = point(first, INGEST_REQUESTS_METRIC).orElseThrow();
        assertEquals(3.0, rate.getValue());
        assertEquals("200", rate.getTags().get("status"));
        assertEquals("platform", rate.getTags().get("source"));
        assertFalse(rate.getTags().containsKey("path"));
        assertFalse(rate.getTags().containsKey("authorization"));

        assertTrue(point(service.collect(), INGEST_REQUESTS_METRIC).isEmpty(),
                "An unchanged counter must not emit a zero-rate point");

        counter.increment(2);
        MetricPoint secondRate = point(service.collect(), INGEST_REQUESTS_METRIC).orElseThrow();
        assertEquals(2.0, secondRate.getValue());
    }

    @Test
    void collectReadsMicrometerBinderNamesAndExportsPrometheusStyleNames() {
        Gauge.builder("tomcat.threads.busy", () -> 2.0)
                .register(meterRegistry);
        Gauge.builder("hikaricp.connections.active", () -> 5.0)
                .register(meterRegistry);

        List<MetricPoint> points = service.collect();

        assertEquals(2.0, point(points, PlatformResourceService.TOMCAT_BUSY_METRIC)
                .orElseThrow().getValue());
        assertEquals(5.0, point(points, PlatformResourceService.DB_CONNECTIONS_METRIC)
                .orElseThrow().getValue());
    }

    @Test
    void collectExportsP95IngestionLatency() {
        Timer timer = Timer.builder(INGEST_LATENCY_METRIC)
                .publishPercentiles(0.95)
                .register(meterRegistry);
        for (int i = 1; i <= 100; i++) {
            timer.record(Duration.ofMillis(i));
        }

        MetricPoint latency = point(service.collect(), INGEST_LATENCY_METRIC).orElseThrow();

        assertEquals("0.95", latency.getTags().get("quantile"));
        assertTrue(latency.getValue() > 0);
        assertEquals(latency.getValue(), timer.percentile(0.95, TimeUnit.SECONDS), 0.0001);
    }

    @Test
    void publishWritesSamplesThroughTheRegularIngestionService() {
        Application systemApplication = Application.builder()
                .id(7L)
                .name(PlatformResourceService.SYSTEM_APPLICATION_NAME)
                .organizationId(9L)
                .isSystem(true)
                .build();
        when(platformResourceService.ensureSystemApplication()).thenReturn(systemApplication);

        service.publish();

        verify(metricIngestService).ingest(org.mockito.ArgumentMatchers.eq(systemApplication),
                any(MetricIngestRequest.class));
    }

    @Test
    void publishSwallowsIngestionFailures() {
        Application systemApplication = Application.builder()
                .id(7L)
                .name(PlatformResourceService.SYSTEM_APPLICATION_NAME)
                .organizationId(9L)
                .isSystem(true)
                .build();
        when(platformResourceService.ensureSystemApplication()).thenReturn(systemApplication);
        org.mockito.Mockito.doThrow(new IllegalStateException("storage unavailable"))
                .when(metricIngestService).ingest(any(Application.class), any(MetricIngestRequest.class));

        service.publish();

        verify(metricIngestService).ingest(org.mockito.ArgumentMatchers.eq(systemApplication),
                any(MetricIngestRequest.class));
    }

    private Optional<MetricPoint> point(List<MetricPoint> points, String name) {
        return points.stream().filter(point -> point.getName().equals(name)).findFirst();
    }
}
