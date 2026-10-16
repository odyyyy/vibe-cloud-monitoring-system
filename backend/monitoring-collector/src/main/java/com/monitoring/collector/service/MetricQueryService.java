package com.monitoring.collector.service;

import com.monitoring.collector.dto.AggregatedMetricResponse;
import com.monitoring.collector.dto.MetricsQueryRequest;
import com.monitoring.collector.repository.MetricBucketProjection;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.storage.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Reads and aggregates stored metrics.
 *
 * <p>Before querying {@code metric_samples}, it verifies that the application belongs to
 * the current user ({@link ApplicationService#getOwnedApplication}). This is a tenant
 * invariant: the application id from the request is never trusted on its own.
 *
 * <p>Aggregation happens in the database (GROUP BY over time buckets), so raw points are
 * not loaded into the application. Range and interval validation happens at the API
 * boundary ({@code @Valid} on {@link MetricsQueryRequest}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricQueryService {

    private static final long SECONDS_PER_MINUTE = 60L;

    private final MetricSampleRepository metricSampleRepository;
    private final ApplicationService applicationService;

    public List<AggregatedMetricResponse> query(Long userId, MetricsQueryRequest request) {
        applicationService.getOwnedApplication(request.getApplicationId(), userId);

        long intervalSeconds = request.getIntervalMinutes().longValue() * SECONDS_PER_MINUTE;

        List<MetricBucketProjection> buckets = switch (request.getAggregation()) {
            case AVG -> metricSampleRepository.aggregateAverage(
                    request.getApplicationId(), request.getMetricName(),
                    request.getFrom(), request.getTo(), intervalSeconds);
            case MAX -> metricSampleRepository.aggregateMaximum(
                    request.getApplicationId(), request.getMetricName(),
                    request.getFrom(), request.getTo(), intervalSeconds);
            case MIN -> metricSampleRepository.aggregateMinimum(
                    request.getApplicationId(), request.getMetricName(),
                    request.getFrom(), request.getTo(), intervalSeconds);
            case SUM -> metricSampleRepository.aggregateSum(
                    request.getApplicationId(), request.getMetricName(),
                    request.getFrom(), request.getTo(), intervalSeconds);
        };

        return buckets.stream()
                .map(bucket -> AggregatedMetricResponse.builder()
                        .timestamp(Instant.ofEpochSecond(bucket.getBucketEpoch() * intervalSeconds))
                        .value(bucket.getAggregatedValue())
                        .build())
                .toList();
    }
}
