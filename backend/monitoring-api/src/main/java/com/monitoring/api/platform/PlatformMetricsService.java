package com.monitoring.api.platform;

import com.monitoring.apps.entity.Application;
import com.monitoring.collector.dto.request.MetricIngestRequest;
import com.monitoring.collector.dto.request.MetricPoint;
import com.monitoring.collector.service.MetricIngestService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.monitoring.api.platform.PlatformResourceService.DB_CONNECTIONS_METRIC;
import static com.monitoring.api.platform.PlatformResourceService.INGEST_LATENCY_METRIC;
import static com.monitoring.api.platform.PlatformResourceService.INGEST_REQUESTS_METRIC;
import static com.monitoring.api.platform.PlatformResourceService.JVM_HEAP_METRIC;
import static com.monitoring.api.platform.PlatformResourceService.TOMCAT_BUSY_METRIC;

/**
 * Dogfoods the ingestion pipeline: the platform's own runtime metrics are sampled from
 * Micrometer and written through the same {@link MetricIngestService} used by customer agents.
 *
 * <p>Only numeric aggregates and HTTP status codes are exported; request bodies, headers and
 * log content are never sent. Ingestion counters are converted to per-interval deltas so the
 * dashboard can plot a true request rate rather than a monotonically increasing total.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitoring.platform-metrics.enabled", havingValue = "true", matchIfMissing = true)
public class PlatformMetricsService {

    private static final Logger log = LoggerFactory.getLogger(PlatformMetricsService.class);

    private static final String SOURCE_TAG = "source";
    private static final String SOURCE_NAME = "platform";
    private static final String STATUS_TAG = "status";
    private static final String APPLICATION_TAG = "applicationId";
    private static final double LATENCY_PERCENTILE = 0.95;

    /**
     * Micrometer binder names use dots; the ingested/Prometheus-facing names use underscores
     * and are defined in {@link PlatformResourceService}.
     */
    private static final String TOMCAT_BUSY_METER = "tomcat.threads.busy";
    private static final String HIKARI_ACTIVE_METER = "hikaricp.connections.active";

    private final MeterRegistry meterRegistry;
    private final MetricIngestService metricIngestService;
    private final PlatformResourceService platformResourceService;

    /** Last cumulative ingestion counter value per application/status pair. */
    private final Map<String, Double> lastCounterValues = new ConcurrentHashMap<>();

    @Scheduled(
            fixedDelayString = "${monitoring.platform-metrics.interval-ms:60000}",
            initialDelayString = "${monitoring.platform-metrics.initial-delay-ms:30000}")
    public void publish() {
        try {
            List<MetricPoint> points = collect();
            if (points.isEmpty()) {
                return;
            }
            Application application = platformResourceService.ensureSystemApplication();
            metricIngestService.ingest(application, MetricIngestRequest.builder().metrics(points).build());
        } catch (RuntimeException ex) {
            // Self-monitoring must never take the application down or spam a stack trace every
            // interval; the failure is still visible in the log.
            log.warn("Platform self-monitoring sample failed: {}", ex.getMessage());
        }
    }

    List<MetricPoint> collect() {
        Instant now = Instant.now();
        List<MetricPoint> points = new ArrayList<>();

        long heapUsed = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        points.add(point(JVM_HEAP_METRIC, heapUsed, now, Map.of("area", "heap")));

        Double tomcatBusy = gaugeSum(TOMCAT_BUSY_METER);
        if (tomcatBusy != null) {
            points.add(point(TOMCAT_BUSY_METRIC, tomcatBusy, now, Map.of()));
        }

        Double activeConnections = gaugeSum(HIKARI_ACTIVE_METER);
        if (activeConnections != null) {
            points.add(point(DB_CONNECTIONS_METRIC, activeConnections, now, Map.of()));
        }

        addIngestionRate(points, now);
        addIngestionLatency(points, now);

        return points;
    }

    private void addIngestionRate(List<MetricPoint> points, Instant now) {
        Map<String, Double> deltasByStatus = new LinkedHashMap<>();

        for (Counter counter : meterRegistry.find(INGEST_REQUESTS_METRIC).counters()) {
            String status = counter.getId().getTag(STATUS_TAG);
            if (status == null) {
                continue;
            }
            String applicationId = counter.getId().getTag(APPLICATION_TAG);
            String key = (applicationId == null ? "unknown" : applicationId) + "|" + status;
            double current = counter.count();
            double previous = lastCounterValues.getOrDefault(key, 0.0);
            double delta = current >= previous ? current - previous : current;
            lastCounterValues.put(key, current);
            if (delta > 0) {
                deltasByStatus.merge(status, delta, Double::sum);
            }
        }

        deltasByStatus.forEach((status, delta) ->
                points.add(point(INGEST_REQUESTS_METRIC, delta, now, Map.of(STATUS_TAG, status))));
    }

    private void addIngestionLatency(List<MetricPoint> points, Instant now) {
        double p95Seconds = Double.NaN;
        for (Timer timer : meterRegistry.find(INGEST_LATENCY_METRIC).timers()) {
            double candidate = timer.percentile(LATENCY_PERCENTILE, TimeUnit.SECONDS);
            if (!Double.isNaN(candidate) && (Double.isNaN(p95Seconds) || candidate > p95Seconds)) {
                p95Seconds = candidate;
            }
        }
        if (!Double.isNaN(p95Seconds)) {
            points.add(point(INGEST_LATENCY_METRIC, p95Seconds, now, Map.of("quantile", "0.95")));
        }
    }

    private Double gaugeSum(String metricName) {
        boolean found = false;
        double total = 0;
        for (Gauge gauge : meterRegistry.find(metricName).gauges()) {
            double value = gauge.value();
            if (Double.isFinite(value)) {
                total += value;
                found = true;
            }
        }
        return found ? total : null;
    }

    private MetricPoint point(String name, double value, Instant timestamp, Map<String, String> extraTags) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put(SOURCE_TAG, SOURCE_NAME);
        tags.putAll(extraTags);
        return MetricPoint.builder()
                .name(name)
                .value(value)
                .timestamp(timestamp)
                .tags(tags)
                .build();
    }
}
