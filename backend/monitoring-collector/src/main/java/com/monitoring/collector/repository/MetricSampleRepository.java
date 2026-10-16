package com.monitoring.collector.repository;

import com.monitoring.collector.entity.MetricSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MetricSampleRepository extends JpaRepository<MetricSample, Long> {

    List<MetricSample> findByApplication_Id(Long applicationId);

    long countByApplication_Id(Long applicationId);

    /**
     * Aggregates metric points into time buckets of {@code intervalSeconds} seconds.
     *
     * <p>Filters strictly by {@code application_id} (tenant scope), metric name and the
     * inclusive range {@code [fromInstant, toInstant]}. The bucket is computed as
     * {@code FLOOR(UNIX_TIMESTAMP(event_time) / intervalSeconds)}; the service multiplies
     * the index back by the bucket width to derive the interval start.
     *
     * <p>The SQL targets MySQL (and H2 in {@code MODE=MySQL} for local tests).
     */
    @Query(value = """
            SELECT FLOOR(UNIX_TIMESTAMP(event_time) / :intervalSeconds) AS bucketEpoch,
                   AVG(metric_value) AS aggregatedValue
            FROM metric_samples
            WHERE application_id = :applicationId
              AND name = :metricName
              AND event_time >= :fromInstant
              AND event_time <= :toInstant
            GROUP BY bucketEpoch
            ORDER BY bucketEpoch
            """, nativeQuery = true)
    List<MetricBucketProjection> aggregateAverage(@Param("applicationId") Long applicationId,
                                                  @Param("metricName") String metricName,
                                                  @Param("fromInstant") Instant from,
                                                  @Param("toInstant") Instant to,
                                                  @Param("intervalSeconds") long intervalSeconds);

    @Query(value = """
            SELECT FLOOR(UNIX_TIMESTAMP(event_time) / :intervalSeconds) AS bucketEpoch,
                   MAX(metric_value) AS aggregatedValue
            FROM metric_samples
            WHERE application_id = :applicationId
              AND name = :metricName
              AND event_time >= :fromInstant
              AND event_time <= :toInstant
            GROUP BY bucketEpoch
            ORDER BY bucketEpoch
            """, nativeQuery = true)
    List<MetricBucketProjection> aggregateMaximum(@Param("applicationId") Long applicationId,
                                                  @Param("metricName") String metricName,
                                                  @Param("fromInstant") Instant from,
                                                  @Param("toInstant") Instant to,
                                                  @Param("intervalSeconds") long intervalSeconds);

    @Query(value = """
            SELECT FLOOR(UNIX_TIMESTAMP(event_time) / :intervalSeconds) AS bucketEpoch,
                   MIN(metric_value) AS aggregatedValue
            FROM metric_samples
            WHERE application_id = :applicationId
              AND name = :metricName
              AND event_time >= :fromInstant
              AND event_time <= :toInstant
            GROUP BY bucketEpoch
            ORDER BY bucketEpoch
            """, nativeQuery = true)
    List<MetricBucketProjection> aggregateMinimum(@Param("applicationId") Long applicationId,
                                                  @Param("metricName") String metricName,
                                                  @Param("fromInstant") Instant from,
                                                  @Param("toInstant") Instant to,
                                                  @Param("intervalSeconds") long intervalSeconds);

    @Query(value = """
            SELECT FLOOR(UNIX_TIMESTAMP(event_time) / :intervalSeconds) AS bucketEpoch,
                   SUM(metric_value) AS aggregatedValue
            FROM metric_samples
            WHERE application_id = :applicationId
              AND name = :metricName
              AND event_time >= :fromInstant
              AND event_time <= :toInstant
            GROUP BY bucketEpoch
            ORDER BY bucketEpoch
            """, nativeQuery = true)
    List<MetricBucketProjection> aggregateSum(@Param("applicationId") Long applicationId,
                                              @Param("metricName") String metricName,
                                              @Param("fromInstant") Instant from,
                                              @Param("toInstant") Instant to,
                                              @Param("intervalSeconds") long intervalSeconds);
}
