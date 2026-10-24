package com.monitoring.alerts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.entity.NotificationEventType;
import com.monitoring.alerts.event.AlertEvent;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Delivers incidents to the PagerDuty Events API v2 (fixed endpoint, no customer-supplied URL).
 *
 * <p>Opened incidents use {@code trigger}, acknowledgments use {@code acknowledge} and recoveries
 * use {@code resolve}. The incident id is used as {@code dedup_key}, so all transitions map to a
 * single PagerDuty incident.
 */
@Component
public class PagerDutyNotificationSender implements NotificationSender {

    private static final URI EVENTS_ENDPOINT =
            URI.create("https://events.pagerduty.com/v2/enqueue");
    private static final String USER_AGENT = "vibemonitoring-alerts/1.0";

    private final ObjectMapper objectMapper;
    private final NotificationHttpClient httpClient;
    private final NotificationLinks links;
    private final AlertApplicationNameResolver applicationNameResolver;

    public PagerDutyNotificationSender(
            ObjectMapper objectMapper,
            NotificationHttpClient httpClient,
            NotificationLinks links,
            AlertApplicationNameResolver applicationNameResolver) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.links = links;
        this.applicationNameResolver = applicationNameResolver;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.PAGERDUTY;
    }

    @Override
    public NotificationSendResult deliver(NotificationChannel channel, AlertEvent event) {
        String routingKey = channel.getPagerDutyRoutingKey();
        if (routingKey == null || routingKey.isBlank()) {
            return NotificationSendResult.permanentFailure("PagerDuty routing key is missing");
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(buildPayload(routingKey.trim(), event));
        } catch (JsonProcessingException ex) {
            return NotificationSendResult.permanentFailure("Failed to serialize PagerDuty event");
        }

        return httpClient.post(EVENTS_ENDPOINT, body, Map.of(
                "Content-Type", "application/json",
                "User-Agent", USER_AGENT));
    }

    /** Builds an Events API v2 payload. Visible for testing. */
    Map<String, Object> buildPayload(String routingKey, AlertEvent event) {
        boolean test = event.incidentId() == null;
        String dedupKey = test
                ? "vibemonitoring-test-" + event.timestamp().getEpochSecond()
                : String.valueOf(event.incidentId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary(event, test));
        payload.put("source", "vibemonitoring");
        payload.put("severity", pagerDutySeverity(event));
        payload.put("timestamp", event.timestamp().toString());

        Map<String, Object> customDetails = new LinkedHashMap<>();
        customDetails.put("eventType", NotificationEventType.fromStatus(event.status()).name());
        customDetails.put("ruleName", event.ruleName());
        customDetails.put("applicationId", event.applicationId());
        customDetails.put("applicationName", applicationNameResolver.resolve(event));
        customDetails.put("value", event.observedValue());
        customDetails.put("threshold", event.threshold());
        customDetails.put("startedAt", event.incidentStartedAt());
        customDetails.put("deepLinkUrl", links.incidentUrl(event.incidentId()));
        payload.put("custom_details", customDetails);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("routing_key", routingKey);
        body.put("event_action", action(event));
        body.put("dedup_key", dedupKey);
        body.put("payload", payload);
        return body;
    }

    private String action(AlertEvent event) {
        return switch (event.status()) {
            case FIRING -> "trigger";
            case ACKNOWLEDGED -> "acknowledge";
            case RESOLVED -> "resolve";
        };
    }

    private String pagerDutySeverity(AlertEvent event) {
        return switch (event.status()) {
            case FIRING -> "critical";
            case ACKNOWLEDGED -> "warning";
            case RESOLVED -> "info";
        };
    }

    private String summary(AlertEvent event, boolean test) {
        String prefix = test ? "[TEST] " : "";
        return switch (event.status()) {
            case FIRING -> prefix + "Incident opened: " + event.ruleName();
            case ACKNOWLEDGED -> prefix + "Incident acknowledged: " + event.ruleName();
            case RESOLVED -> prefix + "Incident resolved: " + event.ruleName();
        };
    }
}
