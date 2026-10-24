package com.monitoring.alerts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.entity.NotificationEventType;
import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.healthcheck.service.SsrfGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Delivers notifications to a Microsoft Teams incoming webhook as a MessageCard.
 *
 * <p>The webhook URL is customer-supplied, so it is validated for SSRF safety at save time and
 * again immediately before every send. Redirects are disabled by the shared HTTP client.
 */
@Component
public class TeamsNotificationSender implements NotificationSender {

    private static final String USER_AGENT = "vibemonitoring-alerts/1.0";
    private static final String HTTPS_SCHEME = "https";
    private static final String HTTP_SCHEME = "http";

    private final SsrfGuard ssrfGuard;
    private final ObjectMapper objectMapper;
    private final NotificationHttpClient httpClient;
    private final NotificationLinks links;
    private final AlertApplicationNameResolver applicationNameResolver;
    private final boolean allowInsecureHttp;

    public TeamsNotificationSender(
            SsrfGuard ssrfGuard,
            ObjectMapper objectMapper,
            NotificationHttpClient httpClient,
            NotificationLinks links,
            AlertApplicationNameResolver applicationNameResolver,
            @Value("${monitoring.alerts.notifications.allow-insecure-http:false}") boolean allowInsecureHttp) {
        this.ssrfGuard = ssrfGuard;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.links = links;
        this.applicationNameResolver = applicationNameResolver;
        this.allowInsecureHttp = allowInsecureHttp;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.TEAMS;
    }

    @Override
    public NotificationSendResult deliver(NotificationChannel channel, AlertEvent event) {
        URI uri = validate(channel.getTeamsWebhookUrl());
        String body;
        try {
            body = objectMapper.writeValueAsString(buildCard(event));
        } catch (JsonProcessingException ex) {
            return NotificationSendResult.permanentFailure("Failed to serialize Teams card");
        }
        return httpClient.post(uri, body, Map.of(
                "Content-Type", "application/json",
                "User-Agent", USER_AGENT));
    }

    /** Builds a MessageCard. Visible for testing. */
    Map<String, Object> buildCard(AlertEvent event) {
        boolean test = event.incidentId() == null;
        String title = title(event, test);

        List<Map<String, String>> facts = new ArrayList<>();
        facts.add(fact("Status", event.status().name()));
        facts.add(fact("Severity", severity(event)));
        facts.add(fact("Rule", event.ruleName()));
        if (event.applicationId() != null) {
            facts.add(fact("Application", nullToDash(applicationNameResolver.resolve(event))));
        }
        if (event.observedValue() != null) {
            facts.add(fact("Observed value", String.valueOf(event.observedValue())));
        }
        facts.add(fact("Threshold", String.valueOf(event.threshold())));
        facts.add(fact("Started", event.incidentStartedAt() == null
                ? event.timestamp().toString()
                : event.incidentStartedAt().toString()));
        facts.add(fact("Event", NotificationEventType.fromStatus(event.status()).name()));

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("activityTitle", title);
        section.put("facts", facts);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("@type", "MessageCard");
        card.put("@context", "https://schema.org/extensions");
        card.put("summary", title);
        card.put("themeColor", themeColor(event));
        card.put("title", title);
        card.put("text", "VibeMonitoring alert notification");
        card.put("sections", List.of(section));

        String deepLink = links.incidentUrl(event.incidentId());
        if (deepLink != null) {
            card.put("potentialAction", List.of(Map.of(
                    "@type", "OpenUri",
                    "name", "View incident",
                    "targets", List.of(Map.of("os", "default", "uri", deepLink)))));
        }
        return card;
    }

    URI validate(String rawUrl) {
        URI uri = ssrfGuard.parse(rawUrl);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        boolean secure = HTTPS_SCHEME.equals(scheme);
        if (!secure && !(allowInsecureHttp && HTTP_SCHEME.equals(scheme))) {
            throw new IllegalArgumentException("Teams webhook URL must use https");
        }
        ssrfGuard.assertPublicHost(uri);
        return uri;
    }

    private String title(AlertEvent event, boolean test) {
        String prefix = test ? "[TEST] " : "";
        return switch (event.status()) {
            case FIRING -> prefix + "Incident opened: " + event.ruleName();
            case ACKNOWLEDGED -> prefix + "Incident acknowledged: " + event.ruleName();
            case RESOLVED -> prefix + "Incident resolved: " + event.ruleName();
        };
    }

    private String severity(AlertEvent event) {
        return switch (event.status()) {
            case FIRING -> "CRITICAL";
            case ACKNOWLEDGED -> "WARNING";
            case RESOLVED -> "INFO";
        };
    }

    private String themeColor(AlertEvent event) {
        return switch (event.status()) {
            case FIRING -> "D13438";
            case ACKNOWLEDGED -> "FFB900";
            case RESOLVED -> "107C10";
        };
    }

    private Map<String, String> fact(String name, String value) {
        return Map.of("name", name, "value", nullToDash(value));
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
