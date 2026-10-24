package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.ScheduledReport;
import com.monitoring.alerts.entity.ScheduledReportChannelType;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders report content as escaped HTML for email or Slack Block Kit. Log messages and metric
 * names are untrusted telemetry and are always escaped; they are never inserted as markup.
 */
@Component
public class ScheduledReportRenderer {

    private static final DateTimeFormatter INSTANT_FORMAT = DateTimeFormatter.ISO_INSTANT;
    private static final int MAX_MESSAGE_DISPLAY_LENGTH = 180;

    public ReportMessage render(ScheduledReport report, ScheduledReportContent content) {
        String subject = "[VibeMonitoring] " + title(report.getFrequency()) + " report: " + report.getName();
        if (report.getChannelType() == ScheduledReportChannelType.EMAIL) {
            return ReportMessage.email(subject, html(content));
        }
        return ReportMessage.slack(subject, slack(content));
    }

    /** Builds the email HTML. Visible for testing. */
    String html(ScheduledReportContent content) {
        StringBuilder body = new StringBuilder();
        body.append("<html><body style=\"font-family:Arial,sans-serif;color:#0f172a;\">")
                .append("<h2>").append(escapeHtml(content.reportName())).append("</h2>")
                .append("<p style=\"color:#475569;\">")
                .append("<strong>Frequency:</strong> ").append(escapeHtml(title(content.frequency())))
                .append("<br/><strong>Period:</strong> ").append(escapeHtml(content.from().toString()))
                .append(" to ").append(escapeHtml(content.to().toString()))
                .append("<br/><strong>Applications:</strong> ").append(content.applicationCount())
                .append("<br/><strong>Health checks:</strong> ").append(content.healthCheckCount())
                .append("</p>");

        if (content.uptime() != null) {
            body.append("<h3>Uptime</h3>");
            ScheduledReportContent.UptimeSection uptime = content.uptime();
            if (uptime.totalChecks() == 0) {
                body.append("<p>No health-check results in this period.</p>");
            } else {
                body.append("<p><strong>")
                        .append(formatPercentage(uptime.percentage()))
                        .append("</strong> up (")
                        .append(uptime.upChecks()).append(" of ").append(uptime.totalChecks())
                        .append(" checks).</p>");
            }
        }

        if (content.metrics() != null) {
            body.append("<h3>Metrics</h3><p>")
                    .append("<strong>Total points:</strong> ").append(content.metrics().totalPoints())
                    .append("<br/><strong>Distinct metrics:</strong> ").append(content.metrics().distinctMetrics())
                    .append("</p>");
            if (content.metrics().topMetrics().isEmpty()) {
                body.append("<p>No metric samples in this period.</p>");
            } else {
                body.append("<table style=\"border-collapse:collapse;\"><thead><tr>")
                        .append(headerCell("Metric")).append(headerCell("Points"))
                        .append("</tr></thead><tbody>");
                for (ScheduledReportContent.CountedName metric : content.metrics().topMetrics()) {
                    body.append("<tr>").append(cell(metric.name())).append(cell(String.valueOf(metric.count())))
                            .append("</tr>");
                }
                body.append("</tbody></table>");
            }
        }

        if (content.errors() != null) {
            body.append("<h3>Top errors</h3><p>")
                    .append("<strong>Total errors:</strong> ").append(content.errors().totalErrors())
                    .append("</p>");
            if (content.errors().topErrors().isEmpty()) {
                body.append("<p>No error logs in this period.</p>");
            } else {
                body.append("<table style=\"border-collapse:collapse;\"><thead><tr>")
                        .append(headerCell("Message")).append(headerCell("Count"))
                        .append("</tr></thead><tbody>");
                for (ScheduledReportContent.CountedMessage error : content.errors().topErrors()) {
                    body.append("<tr>").append(cell(displayMessage(error.message())))
                            .append(cell(String.valueOf(error.count()))).append("</tr>");
                }
                body.append("</tbody></table>");
            }
        }

        body.append("<h3>Incidents</h3>");
        ScheduledReportContent.IncidentsSection incidents = content.incidents();
        body.append("<p><strong>Total:</strong> ").append(incidents.totalIncidents())
                .append("<br/><strong>Unresolved:</strong> ").append(incidents.unresolvedIncidents())
                .append("</p>");
        if (incidents.latest().isEmpty()) {
            body.append("<p>No incidents started in this period.</p>");
        } else {
            body.append("<table style=\"border-collapse:collapse;\"><thead><tr>")
                    .append(headerCell("Rule")).append(headerCell("Status"))
                    .append(headerCell("Started")).append(headerCell("Resolved"))
                    .append("</tr></thead><tbody>");
            for (ScheduledReportContent.IncidentItem incident : incidents.latest()) {
                body.append("<tr>").append(cell(incident.ruleName()))
                        .append(cell(incident.status()))
                        .append(cell(INSTANT_FORMAT.format(incident.triggeredAt())))
                        .append(cell(incident.resolvedAt() == null ? "-" : INSTANT_FORMAT.format(incident.resolvedAt())))
                        .append("</tr>");
            }
            body.append("</tbody></table>");
        }

        body.append("<p style=\"color:#64748b;font-size:12px;\">Generated ")
                .append(escapeHtml(content.generatedAt().toString()))
                .append(" by VibeMonitoring.</p></body></html>");
        return body.toString();
    }

    /** Builds the Slack Block Kit message. Visible for testing. */
    Map<String, Object> slack(ScheduledReportContent content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("text", content.reportName());

        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(block("header", Map.of(
                "type", "plain_text",
                "text", truncatePlain(content.reportName(), 150))));
        blocks.add(section("*Frequency:* " + escapeSlack(title(content.frequency()))
                + "\n*Period:* " + escapeSlack(content.from().toString())
                + " to " + escapeSlack(content.to().toString())
                + "\n*Applications:* " + content.applicationCount()
                + "   *Health checks:* " + content.healthCheckCount()));

        if (content.uptime() != null) {
            ScheduledReportContent.UptimeSection uptime = content.uptime();
            String text = uptime.totalChecks() == 0
                    ? "*Uptime*\nNo health-check results in this period."
                    : "*Uptime*\n" + formatPercentage(uptime.percentage()) + " up ("
                    + uptime.upChecks() + " of " + uptime.totalChecks() + " checks).";
            blocks.add(section(text));
        }

        if (content.metrics() != null) {
            StringBuilder text = new StringBuilder("*Metrics*\n")
                    .append("Total points: ").append(content.metrics().totalPoints())
                    .append("   Distinct metrics: ").append(content.metrics().distinctMetrics());
            if (content.metrics().topMetrics().isEmpty()) {
                text.append("\nNo metric samples in this period.");
            } else {
                text.append("\nTop metrics by points:");
                for (ScheduledReportContent.CountedName metric : content.metrics().topMetrics()) {
                    text.append("\n• ").append(escapeSlack(metric.name()))
                            .append(" — ").append(metric.count());
                }
            }
            blocks.add(section(text.toString()));
        }

        if (content.errors() != null) {
            StringBuilder text = new StringBuilder("*Top errors*\n")
                    .append("Total errors: ").append(content.errors().totalErrors());
            if (content.errors().topErrors().isEmpty()) {
                text.append("\nNo error logs in this period.");
            } else {
                text.append("\n");
                for (ScheduledReportContent.CountedMessage error : content.errors().topErrors()) {
                    text.append("• ").append(error.count()).append("× ")
                            .append(escapeSlack(displayMessage(error.message()))).append('\n');
                }
            }
            blocks.add(section(text.toString()));
        }

        ScheduledReportContent.IncidentsSection incidents = content.incidents();
        StringBuilder incidentText = new StringBuilder("*Incidents*\n")
                .append("Total: ").append(incidents.totalIncidents())
                .append("   Unresolved: ").append(incidents.unresolvedIncidents());
        if (incidents.latest().isEmpty()) {
            incidentText.append("\nNo incidents started in this period.");
        } else {
            incidentText.append("\nLatest:");
            for (ScheduledReportContent.IncidentItem incident : incidents.latest()) {
                incidentText.append("\n• ").append(escapeSlack(incident.ruleName()))
                        .append(" — ").append(escapeSlack(incident.status()))
                        .append(" (").append(INSTANT_FORMAT.format(incident.triggeredAt())).append(')');
            }
        }
        blocks.add(section(incidentText.toString()));

        blocks.add(block("context", Map.of(
                "type", "mrkdwn",
                "text", "Generated " + escapeSlack(content.generatedAt().toString())
                        + " by VibeMonitoring.")));
        message.put("blocks", blocks);
        return message;
    }

    private Map<String, Object> section(String text) {
        return block("section", Map.of("type", "mrkdwn", "text", text));
    }

    private Map<String, Object> block(String type, Map<String, Object> content) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", type);
        block.putAll(content);
        return block;
    }

    private String headerCell(String value) {
        return "<th style=\"padding:4px 8px;border:1px solid #e2e8f0;text-align:left;\">"
                + escapeHtml(value) + "</th>";
    }

    private String cell(String value) {
        return "<td style=\"padding:4px 8px;border:1px solid #e2e8f0;\">" + escapeHtml(value) + "</td>";
    }

    private String displayMessage(String message) {
        String singleLine = message == null ? "" : message.replace('\r', ' ').replace('\n', ' ').trim();
        if (singleLine.length() <= MAX_MESSAGE_DISPLAY_LENGTH) {
            return singleLine;
        }
        return singleLine.substring(0, MAX_MESSAGE_DISPLAY_LENGTH - 1) + "…";
    }

    private String truncatePlain(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    private String formatPercentage(Double percentage) {
        if (percentage == null) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.2f%%", percentage);
    }

    private String title(com.monitoring.alerts.entity.ScheduledReportFrequency frequency) {
        return switch (frequency) {
            case DAILY -> "Daily";
            case WEEKLY -> "Weekly";
            case MONTHLY -> "Monthly";
        };
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Escapes Slack control characters so telemetry cannot create mentions or links. */
    private String escapeSlack(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
