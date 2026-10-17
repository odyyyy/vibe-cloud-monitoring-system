package com.monitoring.collector.service;

import com.monitoring.collector.entity.MetricGranularity;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricAggregateRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricMaintenanceServiceTest {

    private MetricSampleRepository metricSampleRepository;
    private LogEventRepository logEventRepository;
    private MetricAggregateRepository metricAggregateRepository;
    private MetricMaintenanceService service;

    @BeforeEach
    void setUp() {
        metricSampleRepository = Mockito.mock(MetricSampleRepository.class);
        logEventRepository = Mockito.mock(LogEventRepository.class);
        metricAggregateRepository = Mockito.mock(MetricAggregateRepository.class);
        service = new MetricMaintenanceService(
                metricSampleRepository, logEventRepository, metricAggregateRepository);
    }

    @Test
    void downsample_usesHourAlignedCutoffAndDeletesRawSamples() {
        Instant now = Instant.parse("2026-10-09T10:30:00Z");
        long expectedCutoff = Instant.parse("2026-10-08T10:00:00Z").getEpochSecond();

        when(metricAggregateRepository.insertBucketAggregates(
                eq(MetricGranularity.HOURLY.name()), eq(3600L), anyLong())).thenReturn(5);
        when(metricSampleRepository.findIdsBefore(anyLong(), anyInt())).thenReturn(List.of());

        int deleted = service.downsample(now, 24, 1000);

        assertEquals(0, deleted);
        verify(metricAggregateRepository).insertBucketAggregates(
                MetricGranularity.HOURLY.name(), 3600L, expectedCutoff);
        verify(metricSampleRepository).findIdsBefore(expectedCutoff, 1000);
    }

    @Test
    void downsample_deletesInBatchesUntilEmpty() {
        when(metricAggregateRepository.insertBucketAggregates(any(), anyLong(), anyLong())).thenReturn(0);
        when(metricSampleRepository.findIdsBefore(anyLong(), eq(2)))
                .thenReturn(List.of(1L, 2L))
                .thenReturn(List.of(3L))
                .thenReturn(List.of());
        when(metricSampleRepository.deleteByIds(any())).thenReturn(2).thenReturn(1);

        int deleted = service.downsample(Instant.parse("2026-10-09T10:30:00Z"), 24, 2);

        assertEquals(3, deleted);
    }

    @Test
    void enforceRetention_deletesLogsAndAggregatesWithSeparateWindows() {
        Instant now = Instant.parse("2026-10-09T10:00:00Z");
        long logsCutoff = Instant.parse("2026-09-25T10:00:00Z").getEpochSecond();
        long metricsCutoff = Instant.parse("2026-07-11T10:00:00Z").getEpochSecond();

        when(logEventRepository.findIdsBefore(anyLong(), anyInt())).thenReturn(List.of());
        when(metricAggregateRepository.findIdsBefore(anyLong(), anyInt())).thenReturn(List.of());

        int deleted = service.enforceRetention(now, 14, 90, 500);

        assertEquals(0, deleted);
        verify(logEventRepository).findIdsBefore(logsCutoff, 500);
        verify(metricAggregateRepository).findIdsBefore(metricsCutoff, 500);
    }

    @Test
    void downsample_rejectsNonPositiveBatchSize() {
        assertThrows(IllegalArgumentException.class,
                () -> service.downsample(Instant.now(), 24, 0));
        verify(metricAggregateRepository, never()).insertBucketAggregates(any(), anyLong(), anyLong());
    }
}
