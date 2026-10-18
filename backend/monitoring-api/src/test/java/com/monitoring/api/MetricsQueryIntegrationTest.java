package com.monitoring.api;

import com.monitoring.collector.entity.MetricSample;
import com.monitoring.collector.repository.MetricBucketProjection;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the native SQL that aggregates metrics into time buckets.
 *
 * <p>The query targets MySQL and uses {@code UNIX_TIMESTAMP}, so this test runs against
 * H2 in MySQL compatibility mode ({@code MODE=MySQL}); otherwise the function is
 * unavailable and the test would not reflect the target database behavior.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:metrics-query-it;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class MetricsQueryIntegrationTest {

    private static final Instant FROM = Instant.parse("2026-10-09T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-09T23:59:59Z");
    private static final long FIVE_MINUTES_SECONDS = 300L;

    @Autowired
    private MetricSampleRepository metricSampleRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    private Application application;

    @BeforeEach
    void setUp() {
        metricSampleRepository.deleteAll();
        applicationRepository.deleteAll();
        application = applicationRepository.save(Application.builder()
                .name("query-it-app")
                .apiKeyHash("app_query_it")
                .organizationId(1L)
                .build());
    }

    private void saveSample(Application app, String name, double value, Instant timestamp) {
        metricSampleRepository.save(MetricSample.builder()
                .application(app)
                .name(name)
                .value(value)
                .timestamp(timestamp)
                .receivedAt(Instant.now())
                .tags(new HashMap<>())
                .build());
    }

    @Test
    void aggregateAverage_groupsByBucketAndFiltersByName() {
        saveSample(application, "cpu_usage", 10.0, Instant.parse("2026-10-09T12:00:00Z"));
        saveSample(application, "cpu_usage", 20.0, Instant.parse("2026-10-09T12:02:00Z"));
        saveSample(application, "cpu_usage", 30.0, Instant.parse("2026-10-09T12:10:00Z"));
        saveSample(application, "mem_usage", 99.0, Instant.parse("2026-10-09T12:01:00Z"));

        List<MetricBucketProjection> buckets =
                metricSampleRepository.aggregateAverage(application.getId(), "cpu_usage", FROM, TO, FIVE_MINUTES_SECONDS);

        assertEquals(2, buckets.size());
        assertEquals(15.0, buckets.get(0).getAggregatedValue());
        assertEquals(30.0, buckets.get(1).getAggregatedValue());
    }

    @Test
    void aggregateSum_returnsSumPerBucket() {
        saveSample(application, "requests", 1.0, Instant.parse("2026-10-09T12:00:00Z"));
        saveSample(application, "requests", 2.0, Instant.parse("2026-10-09T12:04:00Z"));
        saveSample(application, "requests", 5.0, Instant.parse("2026-10-09T12:10:00Z"));

        List<MetricBucketProjection> buckets =
                metricSampleRepository.aggregateSum(application.getId(), "requests", FROM, TO, FIVE_MINUTES_SECONDS);

        assertEquals(2, buckets.size());
        assertEquals(3.0, buckets.get(0).getAggregatedValue());
        assertEquals(5.0, buckets.get(1).getAggregatedValue());
    }

    @Test
    void aggregateAverage_excludesSamplesOutsideRange() {
        saveSample(application, "cpu_usage", 42.0, Instant.parse("2026-10-08T12:00:00Z"));
        saveSample(application, "cpu_usage", 7.0, Instant.parse("2026-10-09T12:00:00Z"));

        List<MetricBucketProjection> buckets =
                metricSampleRepository.aggregateAverage(application.getId(), "cpu_usage", FROM, TO, FIVE_MINUTES_SECONDS);

        assertEquals(1, buckets.size());
        assertEquals(7.0, buckets.get(0).getAggregatedValue());
    }

    @Test
    void aggregateAverage_isolatesByApplicationId() {
        Application other = applicationRepository.save(Application.builder()
                .name("other-app")
                .apiKeyHash("app_other")
                .organizationId(2L)
                .build());
        saveSample(application, "cpu_usage", 10.0, Instant.parse("2026-10-09T12:00:00Z"));
        saveSample(other, "cpu_usage", 999.0, Instant.parse("2026-10-09T12:00:00Z"));

        List<MetricBucketProjection> buckets =
                metricSampleRepository.aggregateAverage(application.getId(), "cpu_usage", FROM, TO, FIVE_MINUTES_SECONDS);

        assertEquals(1, buckets.size());
        assertEquals(10.0, buckets.get(0).getAggregatedValue());
    }

    @Test
    void findDistinctNames_returnsSortedUniqueNames() {
        saveSample(application, "cpu_usage", 1.0, Instant.parse("2026-10-09T12:00:00Z"));
        saveSample(application, "cpu_usage", 2.0, Instant.parse("2026-10-09T12:01:00Z"));
        saveSample(application, "mem_usage", 3.0, Instant.parse("2026-10-09T12:02:00Z"));

        List<String> names = metricSampleRepository.findDistinctNames(application.getId(), PageRequest.of(0, 500));

        assertEquals(List.of("cpu_usage", "mem_usage"), names);
    }

    @Test
    void findDistinctNames_isolatesByApplicationId() {
        Application other = applicationRepository.save(Application.builder()
                .name("other-names-app")
                .apiKeyHash("app_other_names")
                .organizationId(2L)
                .build());
        saveSample(application, "cpu_usage", 1.0, Instant.parse("2026-10-09T12:00:00Z"));
        saveSample(other, "disk_usage", 1.0, Instant.parse("2026-10-09T12:00:00Z"));

        List<String> names = metricSampleRepository.findDistinctNames(application.getId(), PageRequest.of(0, 500));

        assertEquals(List.of("cpu_usage"), names);
    }
}
