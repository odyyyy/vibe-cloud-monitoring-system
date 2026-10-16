package com.monitoring.collector.repository;

/**
 * Projection of a metric aggregation grouped by time bucket.
 *
 * <p>{@code bucketEpoch} is the bucket index in epoch seconds
 * ({@code UNIX_TIMESTAMP(event_time) / intervalSeconds}); the bucket start is
 * reconstructed as {@code bucketEpoch * intervalSeconds}.
 * {@code aggregatedValue} is the AVG/MAX/MIN/SUM result for the bucket.
 */
public interface MetricBucketProjection {

    Long getBucketEpoch();

    Double getAggregatedValue();
}
