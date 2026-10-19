package com.monitoring.collector.integration;

import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.collector.entity.DailyUsage;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.entity.MetricAggregate;
import com.monitoring.collector.entity.MetricGranularity;
import com.monitoring.collector.entity.MetricSample;
import com.monitoring.collector.entity.SpanStatus;
import com.monitoring.collector.entity.TraceSpan;
import com.monitoring.collector.repository.DailyUsageRepository;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricAggregateRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.collector.repository.TraceSpanRepository;
import com.monitoring.collector.service.UsageRollupService;
import com.monitoring.collector.testsupport.CollectorTestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the usage rollup against a real database: raw + downsampled metric counting,
 * per-day boundaries, idempotency and the unique (organization, day) guarantee.
 */
@SpringBootTest(classes = CollectorTestApplication.class)
class UsageRollupIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 9);
    private static final Instant NOON = Instant.parse("2026-10-09T12:00:00Z");

    @Autowired
    private UsageRollupService usageRollupService;

    @Autowired
    private DailyUsageRepository dailyUsageRepository;

    @Autowired
    private MetricSampleRepository metricSampleRepository;

    @Autowired
    private MetricAggregateRepository metricAggregateRepository;

    @Autowired
    private LogEventRepository logEventRepository;

    @Autowired
    private TraceSpanRepository traceSpanRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    private Application application;

    @BeforeEach
    void setUp() {
        dailyUsageRepository.deleteAll();
        traceSpanRepository.deleteAll();
        metricAggregateRepository.deleteAll();
        metricSampleRepository.deleteAll();
        logEventRepository.deleteAll();
        applicationRepository.deleteAll();
        application = applicationRepository.save(Application.builder()
                .name("usage-app")
                .apiKeyHash("usage-key-hash")
                .organizationId(7L)
                .build());
    }

    @Test
    void rollup_countsRawAndDownsampledMetricsLogsAndTracesWithinTheDay() {
        saveSample("2026-10-09T01:00:00Z");
        saveSample("2026-10-09T23:59:00Z");
        // A sample from the previous day must not leak into this day's total.
        saveSample("2026-10-08T23:00:00Z");
        // Raw rows are gone after downsampling; their volume survives in sample_count.
        metricAggregateRepository.save(aggregate(Instant.parse("2026-10-09T05:00:00Z").getEpochSecond(), 5));

        saveLog("2026-10-09T02:00:00Z");
        saveLog("2026-10-09T03:00:00Z");
        saveLog("2026-10-10T00:30:00Z");

        saveSpan("2026-10-09T04:00:00Z");

        int updated = usageRollupService.rollup(DAY);

        assertEquals(1, updated);
        List<DailyUsage> rows = dailyUsageRepository.findAll();
        assertEquals(1, rows.size());
        DailyUsage row = rows.get(0);
        assertEquals(7L, row.getOrganizationId());
        assertEquals(DAY, row.getUsageDate());
        assertEquals(7L, row.getMetricsCount(), "2 raw + 5 downsampled");
        assertEquals(2L, row.getLogsCount());
        assertEquals(1L, row.getTracesCount());
    }

    @Test
    void rollup_isIdempotentAndKeepsOneRowPerOrganizationAndDay() {
        saveSample("2026-10-09T01:00:00Z");
        saveLog("2026-10-09T02:00:00Z");

        usageRollupService.rollup(DAY);
        usageRollupService.rollup(DAY);

        List<DailyUsage> rows = dailyUsageRepository.findAll();
        assertEquals(1, rows.size());
        assertEquals(1L, rows.get(0).getMetricsCount());
        assertEquals(1L, rows.get(0).getLogsCount());
    }

    private void saveSample(String timestamp) {
        metricSampleRepository.save(MetricSample.builder()
                .application(application)
                .name("cpu_usage")
                .value(1.0)
                .timestamp(Instant.parse(timestamp))
                .receivedAt(NOON)
                .tags(Map.of("host", "s1"))
                .build());
    }

    private void saveLog(String timestamp) {
        logEventRepository.save(LogEvent.builder()
                .application(application)
                .level(LogLevel.INFO)
                .message("event")
                .timestamp(Instant.parse(timestamp))
                .receivedAt(NOON)
                .build());
    }

    private void saveSpan(String startTime) {
        traceSpanRepository.save(TraceSpan.builder()
                .traceId("trace-1")
                .spanId("span-1")
                .application(application)
                .serviceName("svc")
                .operationName("GET /x")
                .startTime(Instant.parse(startTime))
                .endTime(Instant.parse(startTime).plusMillis(5))
                .durationMs(5)
                .status(SpanStatus.OK)
                .build());
    }

    private MetricAggregate aggregate(long bucketEpoch, int sampleCount) {
        return MetricAggregate.builder()
                .application(application)
                .name("cpu_usage")
                .granularity(MetricGranularity.HOURLY)
                .bucketEpoch(bucketEpoch)
                .tagSignature("host=s1")
                .sampleCount(sampleCount)
                .valueAvg(1.0)
                .valueMax(1.0)
                .valueMin(1.0)
                .build();
    }
}
