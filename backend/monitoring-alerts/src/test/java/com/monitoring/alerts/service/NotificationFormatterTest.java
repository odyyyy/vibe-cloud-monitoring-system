package com.monitoring.alerts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.alerts.dto.response.AlertNotificationPayload;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.healthcheck.service.SsrfGuard;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Formatting-only tests for the Slack and email notification senders (no network / SMTP). */
class NotificationFormatterTest {

    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    private AlertEvent event(String ruleName) {
        return new AlertEvent(
                1L, 2L, ruleName, 3L, 4L, AlertRuleType.METRIC_THRESHOLD,
                "system.cpu.usage", AlertCondition.GREATER_THAN, 90.0, 95.0,
                List.of(
                        new AlertEvent.ConditionObservation(
                                "system.cpu.usage", AlertCondition.GREATER_THAN, 90.0, 95.0, true),
                        new AlertEvent.ConditionObservation(
                                "system.memory.usage", AlertCondition.GREATER_THAN, 85.0, 10.0, false)),
                LogicOperator.AND, IncidentStatus.FIRING, NOW, null);
    }

    private AlertEvent logEvent() {
        return new AlertEvent(
                1L, 2L, "Checkout timeouts", 3L, 4L, AlertRuleType.LOG,
                "logs", AlertCondition.GREATER_OR_EQUAL, 5.0, 6.0,
                List.of(new AlertEvent.ConditionObservation(
                        "logs", AlertCondition.GREATER_OR_EQUAL, 5.0, 6.0, true)),
                LogicOperator.AND, IncidentStatus.FIRING, NOW,
                new AlertEvent.LogAlertDetails(
                        LogLevel.ERROR, "timeout", 5, AlertCondition.GREATER_OR_EQUAL, 5L, 6L,
                        "checkout",
                        "https://app.example.com/app/logs?applicationId=4&level=ERROR&searchText=timeout"));
    }

    @Test
    void slackMessageContainsBlockKitHeaderAndConditions() {
        SlackNotificationSender sender = new SlackNotificationSender(
                Mockito.mock(SsrfGuard.class), new ObjectMapper(), 1000, 1000);

        Map<String, Object> message = sender.buildMessage(event("High CPU"));

        assertEquals("Alert firing: High CPU", message.get("text"));
        assertTrue(message.containsKey("blocks"));
        String serialized = message.toString();
        assertTrue(serialized.contains("system.cpu.usage"));
        assertTrue(serialized.contains("breaching"));
    }

    @Test
    void slackLogMessageIncludesObservedCountAndExplorerLink() {
        SlackNotificationSender sender = new SlackNotificationSender(
                Mockito.mock(SsrfGuard.class), new ObjectMapper(), 1000, 1000);

        Map<String, Object> message = sender.buildMessage(logEvent());

        String serialized = message.toString();
        assertTrue(serialized.contains("Observed count"));
        assertTrue(serialized.contains("*Observed count:*\n6"));
        assertTrue(serialized.contains("Open matching logs in Log Explorer"));
    }

    @Test
    void emailLogBodyIncludesObservedCountAndExplorerLink() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = Mockito.mock(ObjectProvider.class);
        EmailNotificationSender sender = new EmailNotificationSender(provider, "no-reply@example.com");

        String body = sender.htmlBody(logEvent());

        assertTrue(body.contains("Observed count:</strong> 6"));
        assertTrue(body.contains("Open matching logs in Log Explorer"));
    }

    @Test
    void webhookPayloadIncludesObservedCountForLogAlerts() {
        AlertNotificationPayload payload = AlertNotificationPayload.from(logEvent());

        assertEquals(6L, payload.logDetails().observedCount());
    }

    @Test
    void emailBodyEscapesUntrustedRuleName() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = Mockito.mock(ObjectProvider.class);
        EmailNotificationSender sender = new EmailNotificationSender(provider, "no-reply@example.com");

        String body = sender.htmlBody(event("<script>alert(1)</script>"));

        assertFalse(body.contains("<script>"));
        assertTrue(body.contains("&lt;script&gt;"));
    }
}
