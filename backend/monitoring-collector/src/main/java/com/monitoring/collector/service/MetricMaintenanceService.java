package com.monitoring.collector.service;

import com.monitoring.collector.entity.MetricGranularity;
import com.monitoring.collector.repository.MetricAggregateRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Background maintenance of stored telemetry: downsampling of old raw metrics into hourly
 * aggregates.
 *
 * <p>Deletion of expired telemetry is owned by the per-organization retention purge
 * ({@code monitoring-retention}); this service only removes the raw samples it has already
 * aggregated. Deletion happens in bounded batches so a single statement never locks a large
 * part of a table. Deletion is performed through repository methods that each run in their
 * own short transaction; the caller is intentionally not transactional, so work committed
 * earlier is preserved if a later batch fails and a repeated run simply continues where the
 * previous one stopped.
 *
 * <p>Idempotency: the downsampling cutoff is aligned to a bucket boundary and the aggregate
 * insert is guarded by {@code WHERE NOT EXISTS} on the target table. Re-running the job
 * therefore never duplicates or overwrites already-materialized aggregates, even if a crash
 * happened after the insert but before (or in the middle of) the raw-data deletion.
 */
@Service
@RequiredArgsConstructor
public class MetricMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MetricMaintenanceService.class);

    static final long SECONDS_PER_HOUR = 3_600L;

    private final MetricSampleRepository metricSampleRepository;
    private final MetricAggregateRepository metricAggregateRepository;

    /**
     * Aggregates raw samples older than {@code rawRetentionHours} into hourly buckets and
     * then deletes those raw rows in batches.
     *
     * @return the number of raw samples deleted
     */
    public int downsample(Instant now, int rawRetentionHours, int batchSize) {
        requirePositive(batchSize, "batchSize");
        if (rawRetentionHours < 1) {
            throw new IllegalArgumentException("rawRetentionHours must be >= 1");
        }

        long cutoffEpoch = now.minus(Duration.ofHours(rawRetentionHours))
                .truncatedTo(ChronoUnit.HOURS)
                .getEpochSecond();

        int aggregated = metricAggregateRepository.insertBucketAggregates(
                MetricGranularity.HOURLY.name(), SECONDS_PER_HOUR, cutoffEpoch);

        int deleted = deleteInBatches(batchSize, cutoffEpoch,
                metricSampleRepository::findIdsBefore,
                metricSampleRepository::deleteByIds);

        log.info("Metric downsampling finished: cutoff={}, aggregates inserted={}, raw samples deleted={}",
                Instant.ofEpochSecond(cutoffEpoch), aggregated, deleted);
        return deleted;
    }

    /**
     * Repeatedly selects ids older than {@code cutoffEpoch} and deletes them, until no more
     * rows match. Each repository call runs in its own short transaction.
     */
    private int deleteInBatches(int batchSize, long cutoffEpoch,
                                BiFunction<Long, Integer, List<Long>> findIds,
                                Function<Collection<Long>, Integer> deleteByIds) {
        int total = 0;
        while (true) {
            List<Long> ids = findIds.apply(cutoffEpoch, batchSize);
            if (ids.isEmpty()) {
                return total;
            }
            int deleted = deleteByIds.apply(ids);
            total += deleted;
            if (deleted == 0 || ids.size() < batchSize) {
                return total;
            }
        }
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be >= 1");
        }
    }
}
