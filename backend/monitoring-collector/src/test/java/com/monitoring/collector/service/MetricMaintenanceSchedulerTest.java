package com.monitoring.collector.service;

import com.monitoring.collector.config.DownsamplingProperties;
import com.monitoring.collector.config.RetentionProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MetricMaintenanceSchedulerTest {

    private final MetricMaintenanceService service = Mockito.mock(MetricMaintenanceService.class);

    @Test
    void disabledJobsDoNotCallTheService() {
        DownsamplingProperties downsampling = new DownsamplingProperties();
        downsampling.setEnabled(false);
        RetentionProperties retention = new RetentionProperties();
        retention.setEnabled(false);
        MetricMaintenanceScheduler scheduler =
                new MetricMaintenanceScheduler(service, downsampling, retention);

        scheduler.downsampleRawMetrics();
        scheduler.enforceRetention();

        verifyNoInteractions(service);
    }

    @Test
    void enabledJobsCallTheServiceWithConfiguredWindow() {
        DownsamplingProperties downsampling = new DownsamplingProperties();
        downsampling.setRawRetentionHours(48);
        downsampling.setBatchSize(100);
        RetentionProperties retention = new RetentionProperties();
        retention.setLogsDays(7);
        retention.setMetricsDays(30);
        retention.setBatchSize(200);
        MetricMaintenanceScheduler scheduler =
                new MetricMaintenanceScheduler(service, downsampling, retention);

        scheduler.downsampleRawMetrics();
        scheduler.enforceRetention();

        verify(service).downsample(any(Instant.class), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(100));
        verify(service).enforceRetention(any(Instant.class), org.mockito.ArgumentMatchers.eq(7),
                org.mockito.ArgumentMatchers.eq(30), org.mockito.ArgumentMatchers.eq(200));
    }
}
