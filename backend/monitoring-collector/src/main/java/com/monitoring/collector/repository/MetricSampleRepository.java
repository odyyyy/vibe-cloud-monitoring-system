package com.monitoring.collector.repository;

import com.monitoring.collector.entity.MetricSample;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MetricSampleRepository extends JpaRepository<MetricSample, Long> {

    /**
     * Number of metric samples with an event time in {@code [from, to)} for the given
     * applications. Scoped by application ids (tenant ownership); an empty id collection must
     * be handled by the caller (an empty {@code IN} clause is not valid SQL).
     */
    @Query("SELECT COUNT(m) FROM MetricSample m"
            + " WHERE m.application.id IN :applicationIds"
            + " AND m.timestamp >= :from AND m.timestamp < :to")
    long countByApplicationsAndTimeRange(@Param("applicationIds") Collection<Long> applicationIds,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);

    /**
     * Ids of raw samples whose event time is strictly before {@code cutoffEpoch}
     * (epoch seconds), bounded by {@code limit}.
     *
     * <p>Used to delete expired raw data in bounded batches so a single statement never
     * locks a large part of the table.
     */
    @Query(value = "SELECT id FROM metric_samples"
            + " WHERE UNIX_TIMESTAMP(event_time) < :cutoffEpoch"
            + " ORDER BY id"
            + " LIMIT :limit", nativeQuery = true)
    List<Long> findIdsBefore(@Param("cutoffEpoch") long cutoffEpoch, @Param("limit") int limit);

    /**
     * Ids of raw samples older than {@code cutoff} for one application, bounded by the
     * {@link Pageable}. Used by the per-organization retention purge; the application id is
     * resolved from the tenant's own applications, so no other tenant's rows can match.
     */
    @Query("SELECT m.id FROM MetricSample m"
            + " WHERE m.application.id = :applicationId AND m.timestamp < :cutoff"
            + " ORDER BY m.id")
    List<Long> findIdsByApplicationBefore(@Param("applicationId") Long applicationId,
                                          @Param("cutoff") Instant cutoff,
                                          Pageable pageable);

    /**
     * Deletes the samples with the given ids. Tag rows are removed by the database via the
     * {@code ON DELETE CASCADE} foreign key.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MetricSample m WHERE m.id IN :ids")
    int deleteByIds(@Param("ids") Collection<Long> ids);

    /**
     * Number of distinct metric names recorded for the given applications since {@code since}.
     *
     * <p>Scoped by application ids so a tenant can only count its own series; an empty id
     * collection must be handled by the caller (an empty {@code IN} clause is not valid SQL).
     */
    @Query("SELECT COUNT(DISTINCT m.name) FROM MetricSample m"
            + " WHERE m.application.id IN :applicationIds AND m.timestamp >= :since")
    long countDistinctNamesSince(@Param("applicationIds") Collection<Long> applicationIds,
                                 @Param("since") Instant since);

    /**
     * Distinct metric names recorded for an application, ordered alphabetically.
     *
     * <p>Filtered strictly by {@code application_id} (tenant scope) and bounded via
     * {@link Pageable} so a single call cannot scan an unbounded number of names.
     */
    @Query("SELECT DISTINCT m.name FROM MetricSample m WHERE m.application.id = :applicationId ORDER BY m.name")
    List<String> findDistinctNames(@Param("applicationId") Long applicationId, Pageable pageable);

    /**
     * Most recent raw values for a metric within a time window, newest first.
     *
     * <p>Used by alert evaluation to obtain the latest observed value. Filtered strictly by
     * {@code application_id} (tenant scope) and bounded via {@link Pageable} (callers request a
     * single row), so an evaluation never scans an unbounded number of samples.
     */
    @Query("SELECT m.value FROM MetricSample m"
            + " WHERE m.application.id = :applicationId"
            + " AND m.name = :metricName"
            + " AND m.timestamp >= :since"
            + " ORDER BY m.timestamp DESC")
    List<Double> findRecentValues(@Param("applicationId") Long applicationId,
                                  @Param("metricName") String metricName,
                                  @Param("since") Instant since,
                                  Pageable pageable);

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

    @Query(value = """
            SELECT FLOOR(UNIX_TIMESTAMP(ms.event_time) / :intervalSeconds) AS bucketEpoch,
                   AVG(ms.metric_value) AS aggregatedValue,
                   t.tag_value AS groupValue
            FROM metric_samples ms
            JOIN metric_sample_tags t ON t.metric_sample_id = ms.id AND t.tag_key = :groupByTag
            WHERE ms.application_id = :applicationId
              AND ms.name = :metricName
              AND ms.event_time >= :fromInstant
              AND ms.event_time <= :toInstant
            GROUP BY bucketEpoch, t.tag_value
            ORDER BY bucketEpoch, t.tag_value
            """, nativeQuery = true)
    List<MetricBucketProjection> aggregateAverageGrouped(@Param("applicationId") Long applicationId,
                                                         @Param("metricName") String metricName,
                                                         @Param("fromInstant") Instant from,
                                                         @Param("toInstant") Instant to,
                                                         @Param("intervalSeconds") long intervalSeconds,
                                                         @Param("groupByTag") String groupByTag);

    @Query(value = """
            SELECT FLOOR(UNIX_TIMESTAMP(ms.event_time) / :intervalSeconds) AS bucketEpoch,
                   MAX(ms.metric_value) AS aggregatedValue,
                   t.tag_value AS groupValue
            FROM metric_samples ms
            JOIN metric_sample_tags t ON t.metric_sample_id = ms.id AND t.tag_key = :groupByTag
            WHERE ms.application_id = :applicationId
              AND ms.name = :metricName
              AND ms.event_time >= :fromInstant
              AND ms.event_time <= :toInstant
            GROUP BY bucketEpoch, t.tag_value
            ORDER BY bucketEpoch, t.tag_value
            """, nativeQuery = true)
    List<MetricBucketProjection> aggregateMaximumGrouped(@Param("applicationId") Long applicationId,
                                                         @Param("metricName") String metricName,
                                                         @Param("fromInstant") Instant from,
                                                         @Param("toInstant") Instant to,
                                                         @Param("intervalSeconds") long intervalSeconds,
                                                         @Param("groupByTag") String groupByTag);

    @Query(value = """
            SELECT FLOOR(UNIX_TIMESTAMP(ms.event_time) / :intervalSeconds) AS bucketEpoch,
                   MIN(ms.metric_value) AS aggregatedValue,
                   t.tag_value AS groupValue
            FROM metric_samples ms
            JOIN metric_sample_tags t ON t.metric_sample_id = ms.id AND t.tag_key = :groupByTag
            WHERE ms.application_id = :applicationId
              AND ms.name = :metricName
              AND ms.event_time >= :fromInstant
              AND ms.event_time <= :toInstant
            GROUP BY bucketEpoch, t.tag_value
            ORDER BY bucketEpoch, t.tag_value
            """, nativeQuery = true)
    List<MetricBucketProjection> aggregateMinimumGrouped(@Param("applicationId") Long applicationId,
                                                         @Param("metricName") String metricName,
                                                         @Param("fromInstant") Instant from,
                                                         @Param("toInstant") Instant to,
                                                         @Param("intervalSeconds") long intervalSeconds,
                                                         @Param("groupByTag") String groupByTag);

    @Query(value = """
            SELECT FLOOR(UNIX_TIMESTAMP(ms.event_time) / :intervalSeconds) AS bucketEpoch,
                   SUM(ms.metric_value) AS aggregatedValue,
                   t.tag_value AS groupValue
            FROM metric_samples ms
            JOIN metric_sample_tags t ON t.metric_sample_id = ms.id AND t.tag_key = :groupByTag
            WHERE ms.application_id = :applicationId
              AND ms.name = :metricName
              AND ms.event_time >= :fromInstant
              AND ms.event_time <= :toInstant
            GROUP BY bucketEpoch, t.tag_value
            ORDER BY bucketEpoch, t.tag_value
            """, nativeQuery = true)
    List<MetricBucketProjection> aggregateSumGrouped(@Param("applicationId") Long applicationId,
                                                     @Param("metricName") String metricName,
                                                     @Param("fromInstant") Instant from,
                                                     @Param("toInstant") Instant to,
                                                     @Param("intervalSeconds") long intervalSeconds,
                                                     @Param("groupByTag") String groupByTag);
}
