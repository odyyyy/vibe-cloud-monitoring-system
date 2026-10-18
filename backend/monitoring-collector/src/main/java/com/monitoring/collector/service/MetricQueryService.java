package com.monitoring.collector.service;

import com.monitoring.collector.dto.AggregatedMetricResponse;
import com.monitoring.collector.dto.MetricsQueryRequest;
import com.monitoring.collector.repository.MetricBucketProjection;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.storage.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;

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

    /** Upper bound on the number of distinct metric names returned per application. */
    private static final int MAX_METRIC_NAMES = 500;

    private final MetricSampleRepository metricSampleRepository;
    private final ApplicationService applicationService;

    public List<AggregatedMetricResponse> query(Long organizationId, MetricsQueryRequest request) {
        applicationService.getOwnedApplication(request.getApplicationId(), organizationId);

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

    /**
     * Returns the distinct metric names known for an application owned by {@code organizationId}.
     * Ownership is verified before reading, so one tenant cannot enumerate another's metrics.
     */
    public List<String> listMetricNames(Long organizationId, Long applicationId) {
        applicationService.getOwnedApplication(applicationId, organizationId);
        return metricSampleRepository.findDistinctNames(applicationId, PageRequest.of(0, MAX_METRIC_NAMES));
    }

    /**
     * Most recent raw value for a metric within {@code [since, now]}, or empty when the
     * application has not reported that metric recently.
     *
     * <p>Ownership is verified before reading, so alert evaluation cannot observe another
     * tenant's series. Missing data returns empty rather than a sentinel value so callers can
     * distinguish "no data" from a real zero.
     */
    public OptionalDouble latestValue(Long organizationId, Long applicationId, String metricName, Instant since) {
        applicationService.getOwnedApplication(applicationId, organizationId);
        List<Double> values = metricSampleRepository.findRecentValues(
                applicationId, metricName, since, PageRequest.of(0, 1));
        return values.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(values.get(0));
    }
}
