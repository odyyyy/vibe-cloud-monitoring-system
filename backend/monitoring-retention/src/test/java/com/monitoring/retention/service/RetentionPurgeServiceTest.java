package com.monitoring.retention.service;

import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricAggregateRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.collector.repository.TraceSpanRepository;
import com.monitoring.retention.config.RetentionProperties;
import com.monitoring.retention.entity.PurgeRunStatus;
import com.monitoring.retention.entity.RetentionDataType;
import com.monitoring.retention.entity.RetentionPurgeRun;
import com.monitoring.retention.repository.RetentionPurgeRunRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionPurgeServiceTest {

    private static final Long ORGANIZATION_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    private RetentionPolicyService policyService;
    private RetentionProperties properties;
    private ApplicationRepository applicationRepository;
    private LogEventRepository logEventRepository;
    private MetricSampleRepository metricSampleRepository;
    private MetricAggregateRepository metricAggregateRepository;
    private TraceSpanRepository traceSpanRepository;
    private RetentionPurgeRunRepository runRepository;
    private SimpleMeterRegistry meterRegistry;
    private RetentionPurgeService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        policyService = Mockito.mock(RetentionPolicyService.class);
        properties = new RetentionProperties();
        applicationRepository = Mockito.mock(ApplicationRepository.class);
        logEventRepository = Mockito.mock(LogEventRepository.class);
        metricSampleRepository = Mockito.mock(MetricSampleRepository.class);
        metricAggregateRepository = Mockito.mock(MetricAggregateRepository.class);
        traceSpanRepository = Mockito.mock(TraceSpanRepository.class);
        runRepository = Mockito.mock(RetentionPurgeRunRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(meterRegistry);

        service = new RetentionPurgeService(policyService, properties, applicationRepository,
                logEventRepository, metricSampleRepository, metricAggregateRepository,
                traceSpanRepository, runRepository, provider);

        when(policyService.effective(ORGANIZATION_ID))
                .thenReturn(new EffectiveRetention(14, 30, 7));
        when(applicationRepository.findByOrganizationId(ORGANIZATION_ID))
                .thenReturn(List.of(application(10L), application(11L)));
    }

    @Test
    void purge_deletesOnlyRowsOfTheOrganizationsApplications() {
        when(logEventRepository.findIdsByApplicationBefore(eq(10L), any(), any()))
                .thenReturn(List.of(1L, 2L))
                .thenReturn(List.of());
        when(logEventRepository.findIdsByApplicationBefore(eq(11L), any(), any()))
                .thenReturn(List.of());
        when(logEventRepository.deleteByIds(any())).thenReturn(2);
        when(metricSampleRepository.findIdsByApplicationBefore(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(metricAggregateRepository.findIdsByApplicationAndBucketBefore(anyLong(), anyLong(), any()))
                .thenReturn(List.of());
        when(traceSpanRepository.findIdsByApplicationBefore(anyLong(), any(), any()))
                .thenReturn(List.of());

        service.purgeOrganization(ORGANIZATION_ID, NOW);

        verify(logEventRepository).findIdsByApplicationBefore(eq(10L), any(), any());
        verify(logEventRepository).findIdsByApplicationBefore(eq(11L), any(), any());
        verify(metricSampleRepository).findIdsByApplicationBefore(eq(10L), any(), any());
        verify(metricAggregateRepository)
                .findIdsByApplicationAndBucketBefore(eq(10L), anyLong(), any());
        verify(traceSpanRepository).findIdsByApplicationBefore(eq(10L), any(), any());
        verify(logEventRepository, never()).findIdsByApplicationBefore(eq(99L), any(), any());

        ArgumentCaptor<RetentionPurgeRun> runs = ArgumentCaptor.forClass(RetentionPurgeRun.class);
        verify(runRepository, times(3)).save(runs.capture());
        RetentionPurgeRun logsRun = runs.getAllValues().stream()
                .filter(run -> run.getDataType() == RetentionDataType.LOGS)
                .findFirst()
                .orElseThrow();
        assertEquals(PurgeRunStatus.SUCCESS, logsRun.getStatus());
        assertEquals(2, logsRun.getRowsDeleted());
        assertEquals(ORGANIZATION_ID, logsRun.getOrganizationId());

        assertEquals(2.0, meterRegistry.counter("retention_rows_deleted_total",
                "dataType", "logs").count());
    }

    @Test
    void purge_stopsAtTheBatchBudgetPerDataType() {
        properties.setBatchSize(2);
        properties.setMaxBatchesPerRun(3);
        when(logEventRepository.findIdsByApplicationBefore(eq(10L), any(), any()))
                .thenReturn(List.of(1L, 2L));
        when(logEventRepository.deleteByIds(any())).thenReturn(2);
        when(metricSampleRepository.findIdsByApplicationBefore(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(metricAggregateRepository.findIdsByApplicationAndBucketBefore(anyLong(), anyLong(), any()))
                .thenReturn(List.of());
        when(traceSpanRepository.findIdsByApplicationBefore(anyLong(), any(), any()))
                .thenReturn(List.of());

        service.purgeOrganization(ORGANIZATION_ID, NOW);

        verify(logEventRepository, times(3)).deleteByIds(any());
    }

    @Test
    void purge_recordsFailureForOneTypeAndContinuesWithTheOthers() {
        when(logEventRepository.findIdsByApplicationBefore(anyLong(), any(), any()))
                .thenThrow(new IllegalStateException("database is unavailable"));
        when(metricSampleRepository.findIdsByApplicationBefore(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(metricAggregateRepository.findIdsByApplicationAndBucketBefore(anyLong(), anyLong(), any()))
                .thenReturn(List.of());
        when(traceSpanRepository.findIdsByApplicationBefore(anyLong(), any(), any()))
                .thenReturn(List.of());

        service.purgeOrganization(ORGANIZATION_ID, NOW);

        ArgumentCaptor<RetentionPurgeRun> runs = ArgumentCaptor.forClass(RetentionPurgeRun.class);
        verify(runRepository, times(3)).save(runs.capture());
        assertEquals(RetentionDataType.LOGS, runs.getAllValues().get(0).getDataType());
        assertEquals(PurgeRunStatus.FAILED, runs.getAllValues().get(0).getStatus());
        assertEquals("Purge failed. See server logs for details.",
                runs.getAllValues().get(0).getErrorMessage());
        assertTrue(runs.getAllValues().stream()
                .filter(run -> run.getDataType() != RetentionDataType.LOGS)
                .allMatch(run -> run.getStatus() == PurgeRunStatus.SUCCESS));
        assertEquals(0.0, meterRegistry.counter("retention_rows_deleted_total",
                "dataType", "logs").count());
    }

    private Application application(Long id) {
        Application application = Application.builder()
                .name("app-" + id)
                .apiKeyHash("hash-" + id)
                .organizationId(ORGANIZATION_ID)
                .build();
        application.setId(id);
        return application;
    }
}
