package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.ScheduledReport;
import com.monitoring.alerts.entity.ScheduledReportChannelType;
import com.monitoring.alerts.entity.ScheduledReportFrequency;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledReportRendererTest {

    private final ScheduledReportRenderer renderer = new ScheduledReportRenderer();

    @Test
    void emailEscapesUntrustedLogMessagesAndMetricNames() {
        ScheduledReportContent content = content("<script>alert('x')</script>", "cpu<script>");
        ScheduledReport report = report(ScheduledReportChannelType.EMAIL);

        String html = renderer.render(report, content).html();

        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
        assertTrue(html.contains("cpu&lt;script&gt;"));
        assertFalse(html.contains("<script>alert"));
    }

    @Test
    void slackEscapesMentionAndLinkSyntaxFromTelemetry() {
        ScheduledReportContent content = content("<!channel> see <https://evil.example|here>", "metric");
        ScheduledReport report = report(ScheduledReportChannelType.SLACK);

        String json = String.valueOf(renderer.render(report, content).slackMessage());

        assertTrue(json.contains("&lt;!channel&gt;"));
        assertTrue(json.contains("&lt;https://evil.example|here&gt;"));
        assertFalse(json.contains("<!channel>"));
    }

    @Test
    void uptimeWithoutDataIsReportedAsUnknownNotZero() {
        ScheduledReportContent content = new ScheduledReportContent(
                "Daily digest",
                ScheduledReportFrequency.DAILY,
                Instant.parse("2026-10-23T09:00:00Z"),
                Instant.parse("2026-10-24T09:00:00Z"),
                Instant.parse("2026-10-24T09:00:01Z"),
                1,
                1,
                new ScheduledReportContent.UptimeSection(0, 0, null),
                new ScheduledReportContent.ErrorsSection(0, List.of()),
                new ScheduledReportContent.MetricsSection(0, 0, List.of()),
                new ScheduledReportContent.IncidentsSection(0, 0, List.of()));

        String html = renderer.render(report(ScheduledReportChannelType.EMAIL), content).html();

        assertTrue(html.contains("No health-check results in this period."));
        assertFalse(html.contains("0.00%"));
    }

    private ScheduledReportContent content(String errorMessage, String metricName) {
        return new ScheduledReportContent(
                "Daily digest",
                ScheduledReportFrequency.DAILY,
                Instant.parse("2026-10-23T09:00:00Z"),
                Instant.parse("2026-10-24T09:00:00Z"),
                Instant.parse("2026-10-24T09:00:01Z"),
                2,
                1,
                new ScheduledReportContent.UptimeSection(9, 10, 90.0),
                new ScheduledReportContent.ErrorsSection(12,
                        List.of(new ScheduledReportContent.CountedMessage(errorMessage, 12))),
                new ScheduledReportContent.MetricsSection(100, 4,
                        List.of(new ScheduledReportContent.CountedName(metricName, 60))),
                new ScheduledReportContent.IncidentsSection(1, 1, List.of(
                        new ScheduledReportContent.IncidentItem(
                                "High CPU", "FIRING", Instant.parse("2026-10-24T08:00:00Z"), null))));
    }

    private ScheduledReport report(ScheduledReportChannelType type) {
        return ScheduledReport.builder()
                .id(1L)
                .organizationId(7L)
                .name("Daily digest")
                .frequency(ScheduledReportFrequency.DAILY)
                .timeOfDay(LocalTime.of(9, 0))
                .channelType(type)
                .recipients(type == ScheduledReportChannelType.EMAIL ? List.of("ops@example.com") : List.of())
                .includeMetrics(true)
                .includeTopErrors(true)
                .includeUptime(true)
                .build();
    }
}
