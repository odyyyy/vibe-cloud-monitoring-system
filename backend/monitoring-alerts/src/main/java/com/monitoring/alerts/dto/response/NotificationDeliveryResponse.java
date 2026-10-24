package com.monitoring.alerts.dto.response;

import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.entity.NotificationDelivery;
import com.monitoring.alerts.entity.NotificationDeliveryStatus;
import com.monitoring.alerts.entity.NotificationEventType;
import com.monitoring.alerts.entity.ScheduledReport;
import com.monitoring.alerts.entity.ScheduledReportChannelType;
import com.monitoring.alerts.service.NotificationMasking;

import java.time.Instant;
import java.util.List;

/** Public delivery-log row. Channel destinations are masked and secrets are never exposed. */
public record NotificationDeliveryResponse(
        Long id,
        Long channelId,
        NotificationChannelType channelType,
        String channelDestination,
        Long incidentId,
        Long scheduledReportId,
        String scheduledReportName,
        NotificationEventType eventType,
        NotificationDeliveryStatus status,
        int attempts,
        Integer lastHttpStatus,
        String lastError,
        Instant createdAt,
        Instant lastAttemptAt,
        boolean test) {

    public static NotificationDeliveryResponse from(
            NotificationDelivery delivery, NotificationChannel channel, ScheduledReport report) {
        NotificationChannelType type = channel == null
                ? reportChannelType(report)
                : channel.getType();
        String destination = channel == null
                ? reportDestination(report)
                : destination(channel);
        return new NotificationDeliveryResponse(
                delivery.getId(),
                delivery.getChannelId(),
                type,
                destination,
                delivery.getIncidentId(),
                delivery.getScheduledReportId(),
                report == null ? null : report.getName(),
                delivery.getEventType(),
                delivery.getStatus(),
                delivery.getAttempts(),
                delivery.getLastHttpStatus(),
                delivery.getLastError(),
                delivery.getCreatedAt(),
                delivery.getLastAttemptAt(),
                delivery.isTest());
    }

    private static NotificationChannelType reportChannelType(ScheduledReport report) {
        if (report == null || report.getChannelType() == null) {
            return null;
        }
        return report.getChannelType() == ScheduledReportChannelType.EMAIL
                ? NotificationChannelType.EMAIL
                : NotificationChannelType.SLACK;
    }

    private static String reportDestination(ScheduledReport report) {
        if (report == null || report.getChannelType() != ScheduledReportChannelType.EMAIL
                || report.getRecipients() == null || report.getRecipients().isEmpty()) {
            return null;
        }
        List<String> recipients = report.getRecipients();
        if (recipients.size() <= 3) {
            return String.join(", ", recipients);
        }
        return String.join(", ", recipients.subList(0, 3)) + " +" + (recipients.size() - 3) + " more";
    }

    private static String destination(NotificationChannel channel) {
        return switch (channel.getType()) {
            case WEBHOOK -> NotificationMasking.maskUrl(channel.getUrl());
            case SLACK -> NotificationMasking.maskUrl(channel.getSlackWebhookUrl());
            case EMAIL -> channel.getEmailAddress();
            case PAGERDUTY -> NotificationMasking.maskSecret(channel.getPagerDutyRoutingKey());
            case TEAMS -> NotificationMasking.maskUrl(channel.getTeamsWebhookUrl());
        };
    }
}
