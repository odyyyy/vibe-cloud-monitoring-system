package com.monitoring.collector.integration;

import com.monitoring.collector.entity.MetricAggregate;
import com.monitoring.collector.entity.MetricGranularity;
import com.monitoring.collector.entity.MetricSample;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricAggregateRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.collector.service.MetricMaintenanceService;
import com.monitoring.collector.testsupport.CollectorTestApplication;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises metric maintenance against a real database: hourly downsampling with raw-data
 * deletion, idempotency on re-run and tag-aware grouping.
 */
@SpringBootTest(classes = CollectorTestApplication.class)
class MetricMaintenanceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    @Autowired
    private MetricMaintenanceService maintenanceService;

    @Autowired
    private MetricSampleRepository metricSampleRepository;

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private MetricAggregateRepository metricAggregateRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    private Application application;

    @BeforeEach
    void setUp() {
        metricAggregateRepository.deleteAll();
        metricSampleRepository.deleteAll();
        logEventRepository.deleteAll();
        applicationRepository.deleteAll();
        application = applicationRepository.save(Application.builder()
                .name("maintenance-app")
                .apiKeyHash("maintenance-key-hash")
                .organizationId(1L)
                .build());
    }

    @Test
    void downsample_createsHourlyAggregateAndDeletesRawSamples() {
        saveSample("2026-10-08T09:10:00Z", 10.0, Map.of("host", "server-1"));
        saveSample("2026-10-08T09:40:00Z", 20.0, Map.of("host", "server-1"));
        saveSample("2026-10-08T09:55:00Z", 30.0, Map.of("host", "server-1"));
        saveSample("2026-10-09T11:00:00Z", 99.0, Map.of("host", "server-1"));

        maintenanceService.downsample(NOW, 24, 1000);

        List<MetricAggregate> aggregates = metricAggregateRepository.findAll();
        assertEquals(1, aggregates.size());
        MetricAggregate aggregate = aggregates.get(0);
        assertEquals(MetricGranularity.HOURLY, aggregate.getGranularity());
        assertEquals(3, aggregate.getSampleCount());
        assertEquals(20.0, aggregate.getValueAvg());
        assertEquals(30.0, aggregate.getValueMax());
        assertEquals(10.0, aggregate.getValueMin());
        assertEquals("host=server-1", aggregate.getTagSignature());
        assertEquals(Instant.parse("2026-10-08T09:00:00Z").getEpochSecond(), aggregate.getBucketEpoch());

        List<MetricSample> remaining = metricSampleRepository.findAll();
        assertEquals(1, remaining.size());
        assertEquals(99.0, remaining.get(0).getValue());
    }

    @Test
    void downsample_groupsByTagSet() {
        saveSample("2026-10-08T09:10:00Z", 10.0, Map.of("host", "s1"));
        saveSample("2026-10-08T09:20:00Z", 20.0, Map.of("host", "s1"));
        saveSample("2026-10-08T09:30:00Z", 100.0, Map.of("host", "s2"));

        maintenanceService.downsample(NOW, 24, 1000);

        List<MetricAggregate> aggregates = metricAggregateRepository.findAll().stream()
                .sorted(Comparator.comparing(MetricAggregate::getTagSignature))
                .toList();
        assertEquals(2, aggregates.size());
        assertEquals("host=s1", aggregates.get(0).getTagSignature());
        assertEquals(2, aggregates.get(0).getSampleCount());
        assertEquals(15.0, aggregates.get(0).getValueAvg());
        assertEquals("host=s2", aggregates.get(1).getTagSignature());
        assertEquals(1, aggregates.get(1).getSampleCount());
        assertEquals(100.0, aggregates.get(1).getValueAvg());
    }

    @Test
    void downsample_isIdempotentAndDoesNotOverwriteExistingAggregates() {
        saveSample("2026-10-08T09:10:00Z", 10.0, Map.of("host", "s1"));
        saveSample("2026-10-08T09:40:00Z", 20.0, Map.of("host", "s1"));
        saveSample("2026-10-08T09:55:00Z", 30.0, Map.of("host", "s1"));

        maintenanceService.downsample(NOW, 24, 1000);

        // Simulate a crash after aggregation but before the raw rows were fully deleted:
        // a leftover raw row is re-inserted and the job runs again.
        saveSample("2026-10-08T09:00:00Z", 999.0, Map.of("host", "s1"));
        maintenanceService.downsample(NOW, 24, 1000);

        List<MetricAggregate> aggregates = metricAggregateRepository.findAll();
        assertEquals(1, aggregates.size());
        assertEquals(3, aggregates.get(0).getSampleCount());
        assertEquals(20.0, aggregates.get(0).getValueAvg());
        assertEquals(0, metricSampleRepository.count());
    }

    @Test
    void downsample_deletesRawSamplesInBatches() {
        saveSample("2026-10-08T09:01:00Z", 1.0, Map.of("host", "s1"));
        saveSample("2026-10-08T09:02:00Z", 2.0, Map.of("host", "s1"));
        saveSample("2026-10-08T09:03:00Z", 3.0, Map.of("host", "s1"));
        saveSample("2026-10-08T09:04:00Z", 4.0, Map.of("host", "s1"));
        saveSample("2026-10-08T09:05:00Z", 5.0, Map.of("host", "s1"));

        maintenanceService.downsample(NOW, 24, 2);

        assertEquals(0, metricSampleRepository.count());
        List<MetricAggregate> aggregates = metricAggregateRepository.findAll();
        assertEquals(1, aggregates.size());
        assertEquals(5, aggregates.get(0).getSampleCount());
    }

    private void saveSample(String timestamp, double value, Map<String, String> tags) {
        metricSampleRepository.save(MetricSample.builder()
                .application(application)
                .name("cpu_usage")
                .value(value)
                .timestamp(Instant.parse(timestamp))
                .receivedAt(NOW)
                .tags(tags)
                .build());
    }

}
