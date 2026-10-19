package com.monitoring.collector.service;

import com.monitoring.apps.entity.Application;
import com.monitoring.apps.repository.ApplicationRepository;
import com.monitoring.collector.entity.DailyUsage;
import com.monitoring.collector.repository.DailyUsageRepository;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricAggregateRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.collector.repository.TraceSpanRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Recomputes the per-organization, per-day telemetry usage for the Usage &amp; Billing page.
 *
 * <p>Each run stores the <em>absolute</em> counts for a day (not deltas), keyed by
 * {@code (organization, usage_date)} with a unique constraint. Re-running the rollup therefore
 * only overwrites the existing row and can never double-count, which makes the job safe to run
 * hourly (to keep today's running total fresh) and again after the day ends (to finalize it).
 *
 * <p>Metric points are counted as raw samples with an event time inside the day <em>plus</em>
 * the sample counts preserved by aggregates for already-downsampled data (raw rows older than
 * the retention window are deleted, so aggregates are the only remaining evidence of volume).
 * Day boundaries are UTC, matching how event times are stored.
 */
@Service
@RequiredArgsConstructor
public class UsageRollupService {

    private static final Logger log = LoggerFactory.getLogger(UsageRollupService.class);

    private final ApplicationRepository applicationRepository;
    private final DailyUsageRepository dailyUsageRepository;
    private final MetricSampleRepository metricSampleRepository;
    private final MetricAggregateRepository metricAggregateRepository;
    private final LogEventRepository logEventRepository;
    private final TraceSpanRepository traceSpanRepository;

    /**
     * Recompute and persist the usage of every organization for {@code day} (UTC).
     *
     * @return number of organizations updated
     */
    @Transactional
    public int rollup(LocalDate day) {
        Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long fromEpoch = from.getEpochSecond();
        long toEpoch = to.getEpochSecond();

        List<Long> organizationIds = applicationRepository.findDistinctOrganizationIds();
        int updated = 0;
        for (Long organizationId : organizationIds) {
            List<Long> applicationIds = applicationRepository.findByOrganizationId(organizationId).stream()
                    .map(Application::getId)
                    .toList();
            if (applicationIds.isEmpty()) {
                continue;
            }

            long metrics = metricSampleRepository
                    .countByApplicationsAndTimeRange(applicationIds, from, to)
                    + metricAggregateRepository
                    .sumSampleCountByApplicationsAndBucketRange(applicationIds, fromEpoch, toEpoch);
            long logs = logEventRepository.countByApplicationsAndTimeRange(applicationIds, from, to);
            long traces = traceSpanRepository.countByApplicationsAndTimeRange(applicationIds, from, to);

            upsert(organizationId, day, metrics, logs, traces);
            updated++;
        }

        log.info("Usage rollup for {} updated {} organization(s)", day, updated);
        return updated;
    }

    private void upsert(Long organizationId, LocalDate day, long metrics, long logs, long traces) {
        DailyUsage usage = dailyUsageRepository
                .findByOrganizationIdAndUsageDate(organizationId, day)
                .orElseGet(() -> DailyUsage.builder()
                        .organizationId(organizationId)
                        .usageDate(day)
                        .build());
        usage.setMetricsCount(metrics);
        usage.setLogsCount(logs);
        usage.setTracesCount(traces);
        dailyUsageRepository.save(usage);
    }
}
