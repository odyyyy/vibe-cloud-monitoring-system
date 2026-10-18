package com.monitoring.collector.service;

import com.monitoring.collector.dto.AggregatedMetricResponse;
import com.monitoring.collector.dto.MetricAggregation;
import com.monitoring.collector.dto.MetricsQueryRequest;
import com.monitoring.collector.repository.MetricBucketProjection;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.exception.ApplicationAccessDeniedException;
import com.monitoring.apps.service.ApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private MetricQueryService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(MetricSampleRepository.class);
        applicationService = Mockito.mock(ApplicationService.class);
        service = new MetricQueryService(repository, applicationService);
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
        };
    }

    @Test
    void query_leftOutWhenRequesterDoesNotOwnApplication() {
        when(applicationService.getOwnedApplication(APP_ID, USER_ID))
                .thenThrow(new ApplicationAccessDeniedException());

        assertThrows(ApplicationAccessDeniedException.class,
                () -> service.query(USER_ID, request(MetricAggregation.AVG, 5)));

        verifyNoInteractions(repository);
    }

    @Test
    void query_mapsBucketsToIntervalStartAndValue() {
        when(applicationService.getOwnedApplication(APP_ID, USER_ID)).thenReturn(application());
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
        when(applicationService.getOwnedApplication(APP_ID, USER_ID)).thenReturn(application());
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

    private Application application() {
        return Application.builder()
                .id(APP_ID)
                .name("my-app")
                .apiKeyHash("app_key_hash")
                .organizationId(USER_ID)
                .build();
    }
}
