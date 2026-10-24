package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.entity.ScheduledReport;
import com.monitoring.alerts.event.AlertEvent;

/**
 * Delivers an alert transition to a single notification channel of one type.
 *
 * <p>Implementations are invoked on a bounded executor (never on the evaluation thread) and must
 * not block indefinitely. A returned result describes one attempt; the delivery service owns
 * retries and persistence. Thrown exceptions are treated as retryable network failures unless
 * they indicate a permanently invalid target.
 */
public interface NotificationSender {

    /** The channel type this sender handles. */
    NotificationChannelType type();

    /**
     * Performs one delivery attempt.
     *
     * @return the outcome of the attempt, including the HTTP status when applicable
     */
    NotificationSendResult deliver(NotificationChannel channel, AlertEvent event);

    /**
     * Performs one scheduled-report delivery attempt.
     *
     * <p>Email reports use the report's validated recipient list and may pass a {@code null}
     * channel; Slack reports require the configured channel. Senders that do not support reports
     * reject the attempt permanently.
     */
    default NotificationSendResult deliverReport(
            NotificationChannel channel, ScheduledReport report, ReportMessage message) {
        return NotificationSendResult.permanentFailure(
                "Scheduled report delivery is not supported for " + type());
    }
}
