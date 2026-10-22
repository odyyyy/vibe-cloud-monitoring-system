package com.monitoring.collector.repository;

import com.monitoring.collector.entity.MetricAggregate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Persistence for downsampled metrics.
 *
 * <p>Aggregation is performed entirely in the database with a single {@code INSERT ... SELECT}
 * so raw points are never loaded into application memory. The statement is guarded by
 * {@code NOT EXISTS} on the target table, which makes a repeated run idempotent: rows already
 * aggregated are not inserted or overwritten, so a crash between aggregation and deletion
 * cannot corrupt existing aggregates.
 *
 * <p>The raw rows are deleted separately in bounded batches to avoid long table locks.
 */
public interface MetricAggregateRepository extends JpaRepository<MetricAggregate, Long> {

    /**
     * Total number of raw samples represented by the aggregates whose bucket falls into
     * {@code [fromEpoch, toEpoch)} (epoch seconds) for the given applications.
     *
     * <p>Used by the usage rollup to count metrics that have already been downsampled (their
     * raw rows are gone); {@code sample_count} preserves the original volume. Scoped by
     * application ids (tenant ownership); an empty id collection must be handled by the caller.
     */
    @Query("SELECT COALESCE(SUM(a.sampleCount), 0) FROM MetricAggregate a"
            + " WHERE a.application.id IN :applicationIds"
            + " AND a.bucketEpoch >= :fromEpoch AND a.bucketEpoch < :toEpoch")
    long sumSampleCountByApplicationsAndBucketRange(@Param("applicationIds") Collection<Long> applicationIds,
                                                    @Param("fromEpoch") long fromEpoch,
                                                    @Param("toEpoch") long toEpoch);

    /**
     * Groups raw metric samples older than {@code cutoffEpoch} into time buckets of
     * {@code bucketSeconds} seconds and inserts the AVG/MAX/MIN aggregates.
     *
     * <p>Samples are grouped by application, metric name, tag signature (a truncated,
     * key-sorted concatenation of the tag set) and the bucket. Rows whose series/bucket
     * already exist are left untouched ({@code WHERE NOT EXISTS}), which keeps the job
     * idempotent.
     *
     * <p>The SQL targets MySQL and H2 in {@code MODE=MySQL} for tests.
     *
     * @return the number of aggregate rows inserted
     */
    @Modifying
    @Transactional
    @Query(value = INSERT_BUCKET_AGGREGATES, nativeQuery = true)
    int insertBucketAggregates(@Param("granularity") String granularity,
                               @Param("bucketSeconds") long bucketSeconds,
                               @Param("cutoffEpoch") long cutoffEpoch);

    /**
     * Ids of aggregated metrics older than {@code cutoffEpoch} (epoch seconds) for one
     * application, bounded by the {@link Pageable}. Used by the per-organization purge so the
     * deletion stays within the tenant that owns the application.
     */
    @Query("SELECT a.id FROM MetricAggregate a"
            + " WHERE a.application.id = :applicationId AND a.bucketEpoch < :cutoffEpoch"
            + " ORDER BY a.id")
    List<Long> findIdsByApplicationAndBucketBefore(@Param("applicationId") Long applicationId,
                                                   @Param("cutoffEpoch") long cutoffEpoch,
                                                   Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM MetricAggregate a WHERE a.id IN :ids")
    int deleteByIds(@Param("ids") Collection<Long> ids);

    String INSERT_BUCKET_AGGREGATES = """
            INSERT INTO metric_aggregates
                (application_id, name, granularity, bucket_epoch, tag_signature,
                 sample_count, value_avg, value_max, value_min)
            SELECT g.application_id, g.name, :granularity, g.bucket_epoch, g.tag_signature,
                   g.sample_count, g.value_avg, g.value_max, g.value_min
            FROM (
                SELECT x.application_id AS application_id,
                       x.name           AS name,
                       x.bucket_epoch   AS bucket_epoch,
                       x.tag_signature  AS tag_signature,
                       COUNT(*)              AS sample_count,
                       AVG(x.sample_value)   AS value_avg,
                       MAX(x.sample_value)   AS value_max,
                       MIN(x.sample_value)   AS value_min
                FROM (
                    SELECT ms.application_id AS application_id,
                           ms.name           AS name,
                           ms.metric_value   AS sample_value,
                           FLOOR(UNIX_TIMESTAMP(ms.event_time) / :bucketSeconds) * :bucketSeconds
                               AS bucket_epoch,
                           LEFT(COALESCE(
                               (SELECT GROUP_CONCAT(CONCAT(t.tag_key, '=', COALESCE(t.tag_value, ''))
                                                    ORDER BY t.tag_key SEPARATOR '|')
                                FROM metric_sample_tags t
                                WHERE t.metric_sample_id = ms.id), ''), 500) AS tag_signature
                    FROM metric_samples ms
                    WHERE UNIX_TIMESTAMP(ms.event_time) < :cutoffEpoch
                ) x
                GROUP BY x.application_id, x.name, x.bucket_epoch, x.tag_signature
            ) g
            WHERE NOT EXISTS (
                SELECT 1 FROM metric_aggregates a
                WHERE a.application_id = g.application_id
                  AND a.name = g.name
                  AND a.granularity = :granularity
                  AND a.bucket_epoch = g.bucket_epoch
                  AND a.tag_signature = g.tag_signature
            )
            """;
}
