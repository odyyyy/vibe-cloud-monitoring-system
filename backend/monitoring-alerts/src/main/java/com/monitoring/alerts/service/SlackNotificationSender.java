package com.monitoring.alerts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.healthcheck.service.SsrfGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Delivers alert notifications to a Slack incoming webhook using Block Kit.
 *
 * <p>Slack webhooks are re-validated (https, {@code slack.com} host, public address) immediately
 * before each request. Values are serialized through Jackson so untrusted characters in rule
 * names or targets cannot break the JSON body; Slack renders the text as plain text/mrkdwn, never
 * as executable content.
 */
@Component
@Slf4j
public class SlackNotificationSender implements NotificationSender {

    private static final String USER_AGENT = "vibemonitoring-alerts/1.0";
    private static final String HTTPS_SCHEME = "https";
    private static final String SLACK_HOST_SUFFIX = "slack.com";

    private final SsrfGuard ssrfGuard;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public SlackNotificationSender(
            SsrfGuard ssrfGuard,
            ObjectMapper objectMapper,
            @Value("${monitoring.alerts.slack.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${monitoring.alerts.slack.request-timeout-ms:10000}") long requestTimeoutMs) {
        this.ssrfGuard = ssrfGuard;
        this.objectMapper = objectMapper;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.SLACK;
    }

    @Override
    public void deliver(NotificationChannel channel, AlertEvent event) {
        URI uri = validate(channel.getSlackWebhookUrl());
        String body;
        try {
            body = objectMapper.writeValueAsString(buildMessage(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize Slack message", ex);
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                log.warn("Slack channel {} responded with status {}", channel.getId(), response.statusCode());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Slack delivery was interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Slack delivery failed: " + ex.getMessage(), ex);
        }
    }

    /** Builds a Block Kit message. Visible for testing. */
    Map<String, Object> buildMessage(AlertEvent event) {
        String status = event.status().name();
        String headline = status.equals("FIRING")
                ? "Alert firing: " + event.ruleName()
                : "Alert resolved: " + event.ruleName();

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("text", headline);

        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(block("header", Map.of(
                "type", "plain_text",
                "text", headline)));

        blocks.add(section(Map.of(
                "Status", status,
                "Rule", event.ruleName())));

        blocks.add(section(Map.of(
                "Rule type", event.type().name(),
                "Logic", event.logicOperator().name(),
                "Timestamp", event.timestamp().toString())));

        blocks.add(block("section", Map.of(
                "type", "mrkdwn",
                "text", "*Conditions*\n" + conditionSummary(event))));

        message.put("blocks", blocks);
        return message;
    }

    private Map<String, Object> section(Map<String, String> fields) {
        List<Map<String, Object>> rendered = new ArrayList<>(fields.size());
        for (Map.Entry<String, String> field : fields.entrySet()) {
            rendered.add(Map.of(
                    "type", "mrkdwn",
                    "text", "*" + field.getKey() + ":*\n" + nullToDash(field.getValue())));
        }
        return block("section", Map.of("fields", rendered));
    }

    private String conditionSummary(AlertEvent event) {
        StringBuilder builder = new StringBuilder();
        for (AlertEvent.ConditionObservation condition : event.conditions()) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append("- ").append(condition.target())
                    .append(' ').append(symbol(condition.operator()))
                    .append(' ').append(condition.threshold());
            if (condition.observedValue() != null) {
                builder.append(" (observed ").append(condition.observedValue()).append(')');
            }
            builder.append(condition.breaching() ? " [breaching]" : " [ok]");
        }
        return builder.isEmpty() ? "No conditions" : builder.toString();
    }

    private String symbol(AlertCondition condition) {
        return switch (condition) {
            case GREATER_THAN -> ">";
            case LESS_THAN -> "<";
            case EQUAL -> "=";
        };
    }

    private Map<String, Object> block(String type, Map<String, Object> content) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", type);
        block.putAll(content);
        return block;
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private URI validate(String rawUrl) {
        URI uri = ssrfGuard.parse(rawUrl);
        if (!HTTPS_SCHEME.equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Slack webhook URL must use https");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals(SLACK_HOST_SUFFIX) && !host.endsWith("." + SLACK_HOST_SUFFIX)) {
            throw new IllegalArgumentException("Slack webhook URL must be a slack.com address");
        }
        ssrfGuard.assertPublicHost(uri);
        return uri;
    }
}
