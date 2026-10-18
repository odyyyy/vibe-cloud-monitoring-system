package com.monitoring.api.service;

import com.monitoring.api.dto.WorkspaceSummaryResponse;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Computes the workspace counters shown on the Overview page.
 *
 * <p>Tenant scope is derived from the authenticated user id: telemetry is counted only
 * across the user's own applications. Counting runs in a single read-only transaction with
 * three aggregate queries, so the endpoint does not fan out per application.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceSummaryService {

    /** Window covered by the telemetry counters. */
    static final Duration WINDOW = Duration.ofHours(24);

    private final ApplicationRepository applicationRepository;
    private final MetricSampleRepository metricSampleRepository;
    private final LogEventRepository logEventRepository;

    @Transactional(readOnly = true)
    public WorkspaceSummaryResponse summarize(Long organizationId, Instant now) {
        List<Long> applicationIds = applicationRepository.findByOrganizationId(organizationId).stream()
                .map(Application::getId)
                .toList();

        if (applicationIds.isEmpty()) {
            return new WorkspaceSummaryResponse(0L, 0L, 0L);
        }

        Instant since = now.minus(WINDOW);
        return new WorkspaceSummaryResponse(
                applicationIds.size(),
                metricSampleRepository.countDistinctNamesSince(applicationIds, since),
                logEventRepository.countSince(applicationIds, since)
        );
    }
}
