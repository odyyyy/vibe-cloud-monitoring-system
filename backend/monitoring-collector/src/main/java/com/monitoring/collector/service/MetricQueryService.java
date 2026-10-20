package com.monitoring.collector.service;

import com.monitoring.collector.dto.response.AggregatedMetricResponse;
import com.monitoring.collector.dto.request.MetricsQueryRequest;
import com.monitoring.collector.export.ExportData;
import com.monitoring.collector.repository.MetricBucketProjection;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.apps.service.ApplicationService;
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
 * the current user or is the internal platform application
 * ({@link ApplicationService#getQueryableApplication}). This is a tenant invariant: the
 * application id from the request is never trusted on its own.
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
        applicationService.getQueryableApplication(request.getApplicationId(), organizationId);

        long intervalSeconds = request.getIntervalMinutes().longValue() * SECONDS_PER_MINUTE;
        String groupByTag = normalizeGroupByTag(request.getGroupByTag());

        List<MetricBucketProjection> buckets = groupByTag == null
                ? aggregate(request, intervalSeconds)
                : aggregateGrouped(request, intervalSeconds, groupByTag);

        return buckets.stream()
                .map(bucket -> AggregatedMetricResponse.builder()
                        .timestamp(Instant.ofEpochSecond(bucket.getBucketEpoch() * intervalSeconds))
                        .value(bucket.getAggregatedValue())
                        .group(bucket.getGroupValue())
                        .build())
                .toList();
    }

    /**
     * Runs the same aggregated query as {@link #query} and truncates the result to
     * {@code limit} rows for a bounded export. The full bucket count is reported so callers
     * can warn the user that filters need narrowing.
     */
    public ExportData<AggregatedMetricResponse> exportRows(Long organizationId,
                                                           MetricsQueryRequest request,
                                                           int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Export limit must be positive");
        }

        List<AggregatedMetricResponse> points = query(organizationId, request);
        List<AggregatedMetricResponse> rows = points.size() > limit
                ? List.copyOf(points.subList(0, limit))
                : points;

        return new ExportData<>(rows, points.size());
    }

    private List<MetricBucketProjection> aggregate(MetricsQueryRequest request, long intervalSeconds) {
        return switch (request.getAggregation()) {
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
    }

    private List<MetricBucketProjection> aggregateGrouped(MetricsQueryRequest request,
                                                          long intervalSeconds,
                                                          String groupByTag) {
        return switch (request.getAggregation()) {
            case AVG -> metricSampleRepository.aggregateAverageGrouped(
                    request.getApplicationId(), request.getMetricName(),
                    request.getFrom(), request.getTo(), intervalSeconds, groupByTag);
            case MAX -> metricSampleRepository.aggregateMaximumGrouped(
                    request.getApplicationId(), request.getMetricName(),
                    request.getFrom(), request.getTo(), intervalSeconds, groupByTag);
            case MIN -> metricSampleRepository.aggregateMinimumGrouped(
                    request.getApplicationId(), request.getMetricName(),
                    request.getFrom(), request.getTo(), intervalSeconds, groupByTag);
            case SUM -> metricSampleRepository.aggregateSumGrouped(
                    request.getApplicationId(), request.getMetricName(),
                    request.getFrom(), request.getTo(), intervalSeconds, groupByTag);
        };
    }

    private String normalizeGroupByTag(String groupByTag) {
        if (groupByTag == null || groupByTag.isBlank()) {
            return null;
        }
        return groupByTag.trim();
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
