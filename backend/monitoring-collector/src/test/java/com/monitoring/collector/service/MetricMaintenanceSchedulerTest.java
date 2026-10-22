package com.monitoring.collector.service;

import com.monitoring.collector.config.DownsamplingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MetricMaintenanceSchedulerTest {

    private final MetricMaintenanceService service = Mockito.mock(MetricMaintenanceService.class);

    @Test
    void disabledDownsamplingDoesNotCallTheService() {
        DownsamplingProperties downsampling = new DownsamplingProperties();
        downsampling.setEnabled(false);
        MetricMaintenanceScheduler scheduler = new MetricMaintenanceScheduler(service, downsampling);

        scheduler.downsampleRawMetrics();

        verifyNoInteractions(service);
    }

    @Test
    void enabledDownsamplingCallsTheServiceWithConfiguredWindow() {
        DownsamplingProperties downsampling = new DownsamplingProperties();
        downsampling.setRawRetentionHours(48);
        downsampling.setBatchSize(100);
        MetricMaintenanceScheduler scheduler = new MetricMaintenanceScheduler(service, downsampling);

        scheduler.downsampleRawMetrics();

        verify(service).downsample(any(Instant.class), org.mockito.ArgumentMatchers.eq(48),
                org.mockito.ArgumentMatchers.eq(100));
    }
}
