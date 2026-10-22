package com.monitoring.retention.service;

import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricAggregateRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.collector.repository.TraceSpanRepository;
import com.monitoring.retention.config.RetentionProperties;
import com.monitoring.retention.entity.PurgeRunStatus;
import com.monitoring.retention.entity.RetentionDataType;
import com.monitoring.retention.entity.RetentionPurgeRun;
import com.monitoring.retention.repository.RetentionPurgeRunRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Deletes expired telemetry for one organization at a time.
 *
 * <p>A tenant boundary is enforced twice: the retention window is read for the exact
 * organization, and every delete is issued per application id obtained from that
 * organization's applications. Native {@code organization_id} columns do not exist on the
 * telemetry tables, so the application set is the authoritative scope; no row of another
 * tenant can ever be reached.
 *
 * <p>Deletion is batched and capped. Each statement removes at most
 * {@code monitoring.retention.batch-size} rows and a single run visits at most
 * {@code monitoring.retention.max-batches-per-run} batches per data type, so a large purge
 * never holds a lock for long and simply continues on the next scheduled run.
 *
 * <p>Every attempt (even one that deletes nothing) is recorded, and failures are recorded
 * with a short message and do not abort the other data types or organizations.
 */
@Service
@RequiredArgsConstructor
public class RetentionPurgeService {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeService.class);
    /**
     * Customer-visible failure text. The real exception is logged server-side; internal
     * database details must not be exposed through the organization's purge history.
     */
    private static final String FAILURE_MESSAGE = "Purge failed. See server logs for details.";
    private static final String ROWS_DELETED_METRIC = "retention_rows_deleted_total";

    private final RetentionPolicyService policyService;
    private final RetentionProperties properties;
    private final ApplicationRepository applicationRepository;
    private final LogEventRepository logEventRepository;
    private final MetricSampleRepository metricSampleRepository;
    private final MetricAggregateRepository metricAggregateRepository;
    private final TraceSpanRepository traceSpanRepository;
    private final RetentionPurgeRunRepository purgeRunRepository;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /** Purges all data types for one organization using its effective policy. */
    public void purgeOrganization(Long organizationId, Instant now) {
        EffectiveRetention retention = policyService.effective(organizationId);
        List<Long> applicationIds = applicationRepository.findByOrganizationId(organizationId).stream()
                .map(Application::getId)
                .toList();

        purgeDataType(organizationId, RetentionDataType.LOGS,
                now.minus(Duration.ofDays(retention.logsDays())), applicationIds);
        purgeDataType(organizationId, RetentionDataType.METRICS,
                now.minus(Duration.ofDays(retention.metricsDays())), applicationIds);
        purgeDataType(organizationId, RetentionDataType.TRACES,
                now.minus(Duration.ofDays(retention.tracesDays())), applicationIds);
    }

    private void purgeDataType(Long organizationId, RetentionDataType dataType,
                               Instant cutoff, List<Long> applicationIds) {
        Instant startedAt = Instant.now();
        long deleted = 0;
        PurgeRunStatus status = PurgeRunStatus.SUCCESS;
        String errorMessage = null;
        try {
            deleted = switch (dataType) {
                case LOGS -> deleteInBatches(applicationIds, properties.getBatchSize(),
                        properties.getMaxBatchesPerRun(),
                        (applicationId, limit) -> logEventRepository.findIdsByApplicationBefore(
                                applicationId, cutoff, PageRequest.of(0, limit)),
                        logEventRepository::deleteByIds).rows();
                case METRICS -> purgeMetrics(applicationIds, cutoff);
                case TRACES -> deleteInBatches(applicationIds, properties.getBatchSize(),
                        properties.getMaxBatchesPerRun(),
                        (applicationId, limit) -> traceSpanRepository.findIdsByApplicationBefore(
                                applicationId, cutoff, PageRequest.of(0, limit)),
                        traceSpanRepository::deleteByIds).rows();
            };
        } catch (RuntimeException ex) {
            status = PurgeRunStatus.FAILED;
            errorMessage = FAILURE_MESSAGE;
            log.error("Retention purge failed (organization={}, dataType={})",
                    organizationId, dataType, ex);
        }

        purgeRunRepository.save(RetentionPurgeRun.builder()
                .organizationId(organizationId)
                .dataType(dataType)
                .startedAt(startedAt)
                .finishedAt(Instant.now())
                .rowsDeleted(deleted)
                .status(status)
                .errorMessage(errorMessage)
                .build());

        incrementDeletedMetric(dataType, deleted);
        log.info("Retention purge finished organization={} dataType={} rowsDeleted={} status={}",
                organizationId, dataType, deleted, status);
    }

    private long purgeMetrics(List<Long> applicationIds, Instant cutoff) {
        long cutoffEpoch = cutoff.getEpochSecond();
        BatchResult rawSamples = deleteInBatches(applicationIds, properties.getBatchSize(),
                properties.getMaxBatchesPerRun(),
                (applicationId, limit) -> metricSampleRepository.findIdsByApplicationBefore(
                        applicationId, cutoff, PageRequest.of(0, limit)),
                metricSampleRepository::deleteByIds);

        int remainingBatches = properties.getMaxBatchesPerRun() - rawSamples.batches();
        if (remainingBatches <= 0) {
            return rawSamples.rows();
        }
        BatchResult aggregates = deleteInBatches(applicationIds, properties.getBatchSize(),
                remainingBatches,
                (applicationId, limit) -> metricAggregateRepository
                        .findIdsByApplicationAndBucketBefore(applicationId, cutoffEpoch,
                                PageRequest.of(0, limit)),
                metricAggregateRepository::deleteByIds);
        return rawSamples.rows() + aggregates.rows();
    }

    /**
     * Repeatedly selects ids for the given applications and deletes them until either no rows
     * remain or the per-run batch budget is exhausted. Each repository call is its own short
     * transaction; the caller is intentionally not transactional so committed batches survive
     * a later failure.
     */
    private BatchResult deleteInBatches(List<Long> applicationIds, int batchSize, int maxBatches,
                                        BiFunction<Long, Integer, List<Long>> findIds,
                                        Function<Collection<Long>, Integer> deleteByIds) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        int totalRows = 0;
        int batches = 0;
        for (Long applicationId : applicationIds) {
            while (batches < maxBatches) {
                List<Long> ids = findIds.apply(applicationId, batchSize);
                if (ids.isEmpty()) {
                    break;
                }
                int deleted = deleteByIds.apply(ids);
                totalRows += deleted;
                batches++;
                if (deleted == 0 || ids.size() < batchSize) {
                    break;
                }
            }
            if (batches >= maxBatches) {
                break;
            }
        }
        return new BatchResult(totalRows, batches);
    }

    private void incrementDeletedMetric(RetentionDataType dataType, long deleted) {
        if (deleted <= 0) {
            return;
        }
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(ROWS_DELETED_METRIC, "dataType", dataType.name().toLowerCase(Locale.ROOT))
                    .increment(deleted);
        }
    }

    private record BatchResult(long rows, int batches) {
    }
}
