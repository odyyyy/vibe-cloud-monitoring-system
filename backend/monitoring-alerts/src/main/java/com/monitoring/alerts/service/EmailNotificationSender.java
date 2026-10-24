package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.entity.ScheduledReport;
import com.monitoring.alerts.event.AlertEvent;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Delivers alert notifications as a formatted HTML email.
 *
 * <p>Sending runs on the dedicated, bounded alert email executor (see
 * {@code AlertNotificationDispatcher}), never on the evaluation thread, so a slow or unreachable
 * SMTP server cannot delay alert evaluation. If SMTP is not configured ({@code spring.mail.host}
 * absent) no {@link JavaMailSender} bean exists and delivery fails fast with a clear message
 * rather than blocking.
 *
 * <p>Runtime values from rule names and metric targets are HTML-escaped before they are embedded,
 * so telemetry-derived text cannot inject markup into the message.
 */
@Component
@Slf4j
public class EmailNotificationSender implements NotificationSender {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;

    public EmailNotificationSender(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${monitoring.alerts.email.from:no-reply@vibemonitoring.local}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public NotificationSendResult deliver(NotificationChannel channel, AlertEvent event) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return NotificationSendResult.permanentFailure(
                    "Email delivery is not configured (spring.mail.host is missing)");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(channel.getEmailAddress());
            helper.setSubject(subject(event));
            helper.setText(htmlBody(event), true);
            mailSender.send(message);
            return NotificationSendResult.delivered();
        } catch (Exception ex) {
            return NotificationSendResult.networkFailure(
                    "SMTP delivery failed (" + ex.getClass().getSimpleName() + ")");
        }
    }

    private String subject(AlertEvent event) {
        return "[VibeMonitoring] Alert " + event.status().name().toLowerCase() + ": " + event.ruleName();
    }

    @Override
    public NotificationSendResult deliverReport(
            NotificationChannel channel, ScheduledReport report, ReportMessage message) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return NotificationSendResult.permanentFailure(
                    "Email delivery is not configured (spring.mail.host is missing)");
        }
        if (report.getRecipients() == null || report.getRecipients().isEmpty()) {
            return NotificationSendResult.permanentFailure("Scheduled report has no email recipients");
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(report.getRecipients().toArray(String[]::new));
            helper.setSubject(message.subject());
            helper.setText(message.html(), true);
            mailSender.send(mimeMessage);
            return NotificationSendResult.delivered();
        } catch (Exception ex) {
            return NotificationSendResult.networkFailure(
                    "SMTP delivery failed (" + ex.getClass().getSimpleName() + ")");
        }
    }

    /** Builds the HTML body. Visible for testing. */
    String htmlBody(AlertEvent event) {
        StringBuilder rows = new StringBuilder();
        for (AlertEvent.ConditionObservation condition : event.conditions()) {
            rows.append("<tr>")
                    .append("<td style=\"padding:4px 8px;border:1px solid #e2e8f0;\">")
                    .append(escape(condition.target())).append("</td>")
                    .append("<td style=\"padding:4px 8px;border:1px solid #e2e8f0;\">")
                    .append(escape(symbol(condition.operator()))).append(' ')
                    .append(condition.threshold()).append("</td>")
                    .append("<td style=\"padding:4px 8px;border:1px solid #e2e8f0;\">")
                    .append(condition.observedValue() == null ? "-" : condition.observedValue()).append("</td>")
                    .append("<td style=\"padding:4px 8px;border:1px solid #e2e8f0;\">")
                    .append(condition.breaching() ? "breaching" : "ok").append("</td>")
                    .append("</tr>");
        }

        return "<html><body style=\"font-family:Arial,sans-serif;color:#0f172a;\">"
                + "<h2>Alert " + escape(event.status().name().toLowerCase()) + ": " + escape(event.ruleName()) + "</h2>"
                + "<p><strong>Status:</strong> " + escape(event.status().name())
                + "<br/><strong>Rule type:</strong> " + escape(event.type().name())
                + "<br/><strong>Logic:</strong> " + escape(event.logicOperator().name())
                + "<br/><strong>Time:</strong> " + escape(event.timestamp().toString()) + "</p>"
                + logDetailsHtml(event.logDetails())
                + "<table style=\"border-collapse:collapse;\">"
                + "<thead><tr>"
                + "<th style=\"padding:4px 8px;border:1px solid #e2e8f0;text-align:left;\">Target</th>"
                + "<th style=\"padding:4px 8px;border:1px solid #e2e8f0;text-align:left;\">Condition</th>"
                + "<th style=\"padding:4px 8px;border:1px solid #e2e8f0;text-align:left;\">Observed</th>"
                + "<th style=\"padding:4px 8px;border:1px solid #e2e8f0;text-align:left;\">State</th>"
                + "</tr></thead><tbody>" + rows + "</tbody></table>"
                + "<p style=\"color:#64748b;font-size:12px;\">Sent by VibeMonitoring.</p>"
                + "</body></html>";
    }

    private String logDetailsHtml(AlertEvent.LogAlertDetails details) {
        if (details == null) {
            return "";
        }
        StringBuilder filters = new StringBuilder();
        if (details.level() != null) {
            filters.append("level ").append(details.level().name());
        }
        if (details.query() != null && !details.query().isBlank()) {
            if (!filters.isEmpty()) {
                filters.append(", ");
            }
            filters.append("query \"").append(details.query()).append('"');
        }
        if (filters.isEmpty()) {
            filters.append("any logs");
        }

        StringBuilder html = new StringBuilder("<p>")
                .append("<strong>Application:</strong> ").append(escape(details.applicationName()))
                .append("<br/><strong>Log filter:</strong> ").append(escape(filters.toString()))
                .append("<br/><strong>Window:</strong> ").append(details.windowMinutes()).append(" min")
                .append("<br/><strong>Threshold:</strong> ").append(escape(symbol(details.comparison())))
                .append(' ').append(details.countThreshold())
                .append("<br/><strong>Observed count:</strong> ").append(details.observedCount());
        if (details.explorerUrl() != null && !details.explorerUrl().isBlank()) {
            html.append("<br/><a href=\"").append(escape(details.explorerUrl()))
                    .append("\">Open matching logs in Log Explorer</a>");
        }
        return html.append("</p>").toString();
    }

    private String symbol(AlertCondition condition) {
        return switch (condition) {
            case GREATER_THAN -> ">";
            case GREATER_OR_EQUAL -> ">=";
            case LESS_THAN -> "<";
            case EQUAL -> "=";
        };
    }

    private String escape(String value) {
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
}
