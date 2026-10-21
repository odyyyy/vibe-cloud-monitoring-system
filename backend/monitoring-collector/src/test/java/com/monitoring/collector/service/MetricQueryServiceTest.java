package com.monitoring.collector.service;

import com.monitoring.collector.dto.response.AggregatedMetricResponse;
import com.monitoring.collector.dto.request.MetricAggregation;
import com.monitoring.collector.dto.request.MetricsQueryRequest;
import com.monitoring.collector.export.ExportData;
import com.monitoring.collector.repository.MetricBucketProjection;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.auth.security.PlatformAdminAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MetricQueryServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long APP_ID = 7L;
    private static final String METRIC = "cpu_usage";
    private static final Instant FROM = Instant.parse("2026-10-09T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-09T01:00:00Z");

    private MetricSampleRepository repository;
    private ApplicationService applicationService;
    private PlatformAdminAccess platformAdminAccess;
    private MetricQueryService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(MetricSampleRepository.class);
        applicationService = Mockito.mock(ApplicationService.class);
        platformAdminAccess = Mockito.mock(PlatformAdminAccess.class);
        service = new MetricQueryService(repository, applicationService, platformAdminAccess);
    }

    private MetricsQueryRequest request(MetricAggregation aggregation, int intervalMinutes) {
        return MetricsQueryRequest.builder()
                .applicationId(APP_ID)
                .metricName(METRIC)
                .from(FROM)
                .to(TO)
                .aggregation(aggregation)
                .intervalMinutes(intervalMinutes)
                .build();
    }

    private MetricBucketProjection bucket(long epoch, double value) {
        return new MetricBucketProjection() {
            @Override
            public Long getBucketEpoch() {
                return epoch;
            }

            @Override
            public Double getAggregatedValue() {
                return value;
            }

            @Override
            public String getGroupValue() {
                return null;
            }
        };
    }

    @Test
    void query_leftOutWhenRequesterDoesNotOwnApplication() {
        when(applicationService.getQueryableApplication(APP_ID, USER_ID, false))
                .thenThrow(new ApplicationAccessDeniedException());

        assertThrows(ApplicationAccessDeniedException.class,
                () -> service.query(USER_ID, request(MetricAggregation.AVG, 5)));

        verifyNoInteractions(repository);
    }

    @Test
    void query_mapsBucketsToIntervalStartAndValue() {
        when(applicationService.getQueryableApplication(APP_ID, USER_ID, false)).thenReturn(application());
        when(repository.aggregateAverage(APP_ID, METRIC, FROM, TO, 300L))
                .thenReturn(List.of(bucket(5_971_764L, 15.0), bucket(5_971_766L, 30.0)));

        List<AggregatedMetricResponse> result = service.query(USER_ID, request(MetricAggregation.AVG, 5));

        assertEquals(2, result.size());
        assertEquals(Instant.ofEpochSecond(5_971_764L * 300L), result.get(0).getTimestamp());
        assertEquals(15.0, result.get(0).getValue());
        assertEquals(Instant.ofEpochSecond(5_971_766L * 300L), result.get(1).getTimestamp());
        assertEquals(30.0, result.get(1).getValue());
    }

    @Test
    void query_dispatchesToRepositoryMethodMatchingAggregation() {
        when(applicationService.getQueryableApplication(APP_ID, USER_ID, false)).thenReturn(application());
        when(repository.aggregateMaximum(APP_ID, METRIC, FROM, TO, 60L)).thenReturn(List.of());
        when(repository.aggregateMinimum(APP_ID, METRIC, FROM, TO, 60L)).thenReturn(List.of());
        when(repository.aggregateSum(APP_ID, METRIC, FROM, TO, 60L)).thenReturn(List.of());

        service.query(USER_ID, request(MetricAggregation.MAX, 1));
        service.query(USER_ID, request(MetricAggregation.MIN, 1));
        service.query(USER_ID, request(MetricAggregation.SUM, 1));

        verify(repository).aggregateMaximum(APP_ID, METRIC, FROM, TO, 60L);
        verify(repository, never()).aggregateAverage(APP_ID, METRIC, FROM, TO, 60L);
        verify(repository).aggregateMinimum(APP_ID, METRIC, FROM, TO, 60L);
        verify(repository).aggregateSum(APP_ID, METRIC, FROM, TO, 60L);
    }

    @Test
    void exportRows_truncatesToLimitAndReportsTotal() {
        when(applicationService.getQueryableApplication(APP_ID, USER_ID, false)).thenReturn(application());
        when(repository.aggregateAverage(APP_ID, METRIC, FROM, TO, 300L))
                .thenReturn(List.of(bucket(1L, 1.0), bucket(2L, 2.0), bucket(3L, 3.0)));

        ExportData<AggregatedMetricResponse> data =
                service.exportRows(USER_ID, request(MetricAggregation.AVG, 5), 2);

        assertEquals(2, data.rows().size());
        assertEquals(3, data.totalRows());
        assertTrue(data.isTruncated());
        assertEquals(1.0, data.rows().get(0).getValue());
        assertEquals(2.0, data.rows().get(1).getValue());
    }

    @Test
    void exportRows_returnsAllRowsWhenUnderLimit() {
        when(applicationService.getQueryableApplication(APP_ID, USER_ID, false)).thenReturn(application());
        when(repository.aggregateAverage(APP_ID, METRIC, FROM, TO, 300L))
                .thenReturn(List.of(bucket(1L, 1.0)));

        ExportData<AggregatedMetricResponse> data =
                service.exportRows(USER_ID, request(MetricAggregation.AVG, 5), 10);

        assertEquals(1, data.rows().size());
        assertEquals(1, data.totalRows());
        assertFalse(data.isTruncated());
    }

    private Application application() {
        return Application.builder()
                .id(APP_ID)
                .name("my-app")
                .apiKeyHash("app_key_hash")
                .organizationId(USER_ID)
                .build();
    }
}
