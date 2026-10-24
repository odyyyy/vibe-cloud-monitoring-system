package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.ScheduledReportFrequency;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated, bounded report data. Every section is optional so a report that does not include a
 * particular capability does not query or render it.
 */
public record ScheduledReportContent(
        String reportName,
        ScheduledReportFrequency frequency,
        Instant from,
        Instant to,
        Instant generatedAt,
        int applicationCount,
        int healthCheckCount,
        UptimeSection uptime,
        ErrorsSection errors,
        MetricsSection metrics,
        IncidentsSection incidents) {

    public record UptimeSection(long upChecks, long totalChecks, Double percentage) {
    }

    public record ErrorsSection(long totalErrors, List<CountedMessage> topErrors) {
    }

    public record MetricsSection(long totalPoints, long distinctMetrics, List<CountedName> topMetrics) {
    }

    public record IncidentsSection(long totalIncidents, long unresolvedIncidents, List<IncidentItem> latest) {
    }

    /** {@code message} is untrusted log text and must be rendered as plain text. */
    public record CountedMessage(String message, long count) {
    }

    public record CountedName(String name, long count) {
    }

    public record IncidentItem(String ruleName, String status, Instant triggeredAt, Instant resolvedAt) {
    }
}
