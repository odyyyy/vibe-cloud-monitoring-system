package com.monitoring.collector.service;

import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.collector.entity.DailyUsage;
import com.monitoring.collector.repository.DailyUsageRepository;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricAggregateRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.collector.repository.TraceSpanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageRollupServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 9);
    private static final Instant FROM = Instant.parse("2026-10-09T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-10T00:00:00Z");

    private ApplicationRepository applicationRepository;
    private DailyUsageRepository dailyUsageRepository;
    private MetricSampleRepository metricSampleRepository;
    private MetricAggregateRepository metricAggregateRepository;
    private LogEventRepository logEventRepository;
    private TraceSpanRepository traceSpanRepository;
    private UsageRollupService service;

    @BeforeEach
    void setUp() {
        applicationRepository = Mockito.mock(ApplicationRepository.class);
        dailyUsageRepository = Mockito.mock(DailyUsageRepository.class);
        metricSampleRepository = Mockito.mock(MetricSampleRepository.class);
        metricAggregateRepository = Mockito.mock(MetricAggregateRepository.class);
        logEventRepository = Mockito.mock(LogEventRepository.class);
        traceSpanRepository = Mockito.mock(TraceSpanRepository.class);
        service = new UsageRollupService(applicationRepository, dailyUsageRepository,
                metricSampleRepository, metricAggregateRepository, logEventRepository,
                traceSpanRepository);
    }

    @Test
    void rollup_countsRawAndDownsampledMetricsAndPersistsDay() {
        when(applicationRepository.findDistinctOrganizationIds()).thenReturn(List.of(1L));
        when(applicationRepository.findByOrganizationId(1L)).thenReturn(List.of(application(10L), application(11L)));
        when(metricSampleRepository.countByApplicationsAndTimeRange(
                eq(List.of(10L, 11L)), eq(FROM), eq(TO))).thenReturn(40L);
        when(metricAggregateRepository.sumSampleCountByApplicationsAndBucketRange(
                eq(List.of(10L, 11L)), eq(FROM.getEpochSecond()), eq(TO.getEpochSecond()))).thenReturn(60L);
        when(logEventRepository.countByApplicationsAndTimeRange(
                eq(List.of(10L, 11L)), eq(FROM), eq(TO))).thenReturn(7L);
        when(traceSpanRepository.countByApplicationsAndTimeRange(
                eq(List.of(10L, 11L)), eq(FROM), eq(TO))).thenReturn(3L);
        when(dailyUsageRepository.findByOrganizationIdAndUsageDate(1L, DAY)).thenReturn(Optional.empty());

        int updated = service.rollup(DAY);

        assertEquals(1, updated);
        ArgumentCaptor<DailyUsage> captor = ArgumentCaptor.forClass(DailyUsage.class);
        verify(dailyUsageRepository).save(captor.capture());
        DailyUsage saved = captor.getValue();
        assertEquals(1L, saved.getOrganizationId());
        assertEquals(DAY, saved.getUsageDate());
        assertEquals(100L, saved.getMetricsCount(), "raw + downsampled metrics");
        assertEquals(7L, saved.getLogsCount());
        assertEquals(3L, saved.getTracesCount());
    }

    @Test
    void rollup_overwritesExistingRowWithAbsoluteValuesInsteadOfAccumulating() {
        DailyUsage existing = DailyUsage.builder()
                .id(99L)
                .organizationId(1L)
                .usageDate(DAY)
                .metricsCount(5L)
                .logsCount(5L)
                .tracesCount(5L)
                .build();
        when(applicationRepository.findDistinctOrganizationIds()).thenReturn(List.of(1L));
        when(applicationRepository.findByOrganizationId(1L)).thenReturn(List.of(application(10L)));
        when(metricSampleRepository.countByApplicationsAndTimeRange(any(), any(), any())).thenReturn(12L);
        when(dailyUsageRepository.findByOrganizationIdAndUsageDate(1L, DAY)).thenReturn(Optional.of(existing));

        service.rollup(DAY);

        assertSame(existing, capturedSave());
        assertEquals(12L, existing.getMetricsCount(), "recomputed, not added to the previous 5");
    }

    @Test
    void rollup_skipsOrganizationsWithoutApplications() {
        when(applicationRepository.findDistinctOrganizationIds()).thenReturn(List.of(1L, 2L));
        when(applicationRepository.findByOrganizationId(1L)).thenReturn(List.of());
        when(applicationRepository.findByOrganizationId(2L)).thenReturn(List.of(application(20L)));

        int updated = service.rollup(DAY);

        assertEquals(1, updated);
        verify(dailyUsageRepository, never()).findByOrganizationIdAndUsageDate(eq(1L), any());
        verify(dailyUsageRepository).save(any(DailyUsage.class));
    }

    @Test
    void rollup_doesNothingWhenThereAreNoOrganizations() {
        when(applicationRepository.findDistinctOrganizationIds()).thenReturn(List.of());

        assertEquals(0, service.rollup(DAY));
        verify(dailyUsageRepository, never()).save(any());
    }

    private DailyUsage capturedSave() {
        ArgumentCaptor<DailyUsage> captor = ArgumentCaptor.forClass(DailyUsage.class);
        verify(dailyUsageRepository).save(captor.capture());
        return captor.getValue();
    }

    private Application application(Long id) {
        return Application.builder()
                .id(id)
                .name("app-" + id)
                .apiKeyHash("hash-" + id)
                .organizationId(1L)
                .build();
    }
}
