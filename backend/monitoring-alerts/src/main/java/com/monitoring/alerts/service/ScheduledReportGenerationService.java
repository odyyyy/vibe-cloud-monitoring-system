package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.AlertIncident;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.ScheduledReport;
import com.monitoring.alerts.repository.AlertIncidentRepository;
import com.monitoring.apps.entity.Application;
import com.monitoring.apps.service.ApplicationService;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.collector.repository.MetricSampleRepository;
import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.repository.HealthCheckConfigRepository;
import com.monitoring.healthcheck.repository.HealthCheckResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Builds bounded report sections with tenant-scoped aggregate queries. Every query is capped by a
 * database query timeout; no raw log or metric rows are loaded into application memory.
 */
@Service
@RequiredArgsConstructor
public class ScheduledReportGenerationService {

    private static final int TOP_ITEMS = 5;
    private static final List<IncidentStatus> UNRESOLVED =
            List.of(IncidentStatus.FIRING, IncidentStatus.ACKNOWLEDGED);

    private final ApplicationService applicationService;
    private final HealthCheckConfigRepository healthCheckConfigRepository;
    private final HealthCheckResultRepository healthCheckResultRepository;
    private final LogEventRepository logEventRepository;
    private final MetricSampleRepository metricSampleRepository;
    private final AlertIncidentRepository alertIncidentRepository;

    @Transactional(readOnly = true, timeout = 30)
    public ScheduledReportContent generate(Long organizationId, ScheduledReport report, Instant generatedAt) {
        Duration window = switch (report.getFrequency()) {
            case DAILY -> Duration.ofDays(1);
            case WEEKLY -> Duration.ofDays(7);
            case MONTHLY -> Duration.ofDays(30);
        };
        Instant to = generatedAt;
        Instant from = to.minus(window);

        List<Long> applicationIds = applicationService.findByOrganizationId(organizationId).stream()
                .filter(application -> !application.isSystem())
                .map(Application::getId)
                .toList();
        List<Long> healthCheckIds = healthCheckConfigRepository
                .findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(HealthCheckConfig::getId)
                .toList();

        return new ScheduledReportContent(
                report.getName(),
                report.getFrequency(),
                from,
                to,
                generatedAt,
                applicationIds.size(),
                healthCheckIds.size(),
                report.isIncludeUptime() ? uptime(healthCheckIds, from, to) : null,
                report.isIncludeTopErrors() ? errors(applicationIds, from, to) : null,
                report.isIncludeMetrics() ? metrics(applicationIds, from, to) : null,
                incidents(organizationId, from, to));
    }

    private ScheduledReportContent.UptimeSection uptime(List<Long> healthCheckIds, Instant from, Instant to) {
        if (healthCheckIds.isEmpty()) {
            return new ScheduledReportContent.UptimeSection(0, 0, null);
        }
        long total = healthCheckResultRepository.countByConfigIdsAndTimeRange(healthCheckIds, from, to);
        long up = healthCheckResultRepository.countUpByConfigIdsAndTimeRange(healthCheckIds, from, to);
        Double percentage = total == 0 ? null : (up * 100.0) / total;
        return new ScheduledReportContent.UptimeSection(up, total, percentage);
    }

    private ScheduledReportContent.ErrorsSection errors(List<Long> applicationIds, Instant from, Instant to) {
        if (applicationIds.isEmpty()) {
            return new ScheduledReportContent.ErrorsSection(0, List.of());
        }
        long total = logEventRepository.countErrorsByApplicationsAndTimeRange(applicationIds, from, to);
        List<ScheduledReportContent.CountedMessage> top = logEventRepository
                .findTopErrorMessages(applicationIds, from, to, PageRequest.of(0, TOP_ITEMS)).stream()
                .map(row -> new ScheduledReportContent.CountedMessage(
                        row[0] == null ? "" : row[0].toString(),
                        ((Number) row[1]).longValue()))
                .toList();
        return new ScheduledReportContent.ErrorsSection(total, top);
    }

    private ScheduledReportContent.MetricsSection metrics(List<Long> applicationIds, Instant from, Instant to) {
        if (applicationIds.isEmpty()) {
            return new ScheduledReportContent.MetricsSection(0, 0, List.of());
        }
        long total = metricSampleRepository.countByApplicationsAndTimeRange(applicationIds, from, to);
        long distinct = metricSampleRepository.countDistinctNamesInRange(applicationIds, from, to);
        List<ScheduledReportContent.CountedName> top = metricSampleRepository
                .findTopMetricNames(applicationIds, from, to, PageRequest.of(0, TOP_ITEMS)).stream()
                .map(row -> new ScheduledReportContent.CountedName(
                        row[0] == null ? "" : row[0].toString(),
                        ((Number) row[1]).longValue()))
                .toList();
        return new ScheduledReportContent.MetricsSection(total, distinct, top);
    }

    private ScheduledReportContent.IncidentsSection incidents(Long organizationId, Instant from, Instant to) {
        long total = alertIncidentRepository.countByOrganizationIdAndTimeRange(organizationId, from, to);
        long unresolved = alertIncidentRepository.countByOrganizationIdAndTimeRangeAndStatusIn(
                organizationId, from, to, UNRESOLVED);
        List<ScheduledReportContent.IncidentItem> latest = alertIncidentRepository
                .findByOrganizationIdAndTriggeredAtGreaterThanEqualAndTriggeredAtLessThanOrderByTriggeredAtDesc(
                        organizationId, from, to, PageRequest.of(0, TOP_ITEMS)).stream()
                .map(this::incident)
                .toList();
        return new ScheduledReportContent.IncidentsSection(total, unresolved, latest);
    }

    private ScheduledReportContent.IncidentItem incident(AlertIncident incident) {
        return new ScheduledReportContent.IncidentItem(
                incident.getRule().getName(),
                incident.getStatus().name(),
                incident.getTriggeredAt(),
                incident.getResolvedAt());
    }
}
