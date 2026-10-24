package com.monitoring.alerts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.alerts.dto.response.AlertNotificationPayload;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.healthcheck.service.SsrfGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Delivers alert notifications as a JSON POST to a customer's HTTPS webhook.
 *
 * <p>The URL is re-validated (https, public host) immediately before each request and redirects
 * are disabled. When a secret is configured the exact request body is signed with HMAC-SHA256 and
 * sent in {@code X-Signature-256} together with {@code X-Timestamp}. Static headers are limited to
 * validated names; platform-managed headers cannot be overridden.
 */
@Component
public class WebhookNotificationSender implements NotificationSender {

    private static final String USER_AGENT = "vibemonitoring-alerts/1.0";
    private static final String HTTPS_SCHEME = "https";
    private static final String HTTP_SCHEME = "http";

    private final SsrfGuard ssrfGuard;
    private final ObjectMapper objectMapper;
    private final NotificationHttpClient httpClient;
    private final NotificationLinks links;
    private final AlertApplicationNameResolver applicationNameResolver;
    private final boolean allowInsecureHttp;

    public WebhookNotificationSender(
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
        return NotificationChannelType.WEBHOOK;
    }

    @Override
    public NotificationSendResult deliver(NotificationChannel channel, AlertEvent event) {
        URI uri = validate(channel.getUrl());
        boolean test = event.incidentId() == null;
        AlertNotificationPayload payload = AlertNotificationPayload.from(
                event,
                applicationNameResolver.resolve(event),
                links.incidentUrl(event.incidentId()),
                test);
        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return NotificationSendResult.permanentFailure("Failed to serialize webhook payload");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", USER_AGENT);
        if (channel.getWebhookSecret() != null && !channel.getWebhookSecret().isBlank()) {
            headers.put("X-Signature-256", WebhookSignature.sign(channel.getWebhookSecret(), body));
            headers.put("X-Timestamp", Long.toString(Instant.now().getEpochSecond()));
        }
        headers.putAll(WebhookHeaderCodec.parse(channel.getWebhookHeaders()));

        return httpClient.post(uri, body, headers);
    }

    /** Visible for testing: validates a webhook/Teams URL against the SSRF policy. */
    URI validate(String rawUrl) {
        URI uri = ssrfGuard.parse(rawUrl);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        boolean secure = HTTPS_SCHEME.equals(scheme);
        if (!secure && !(allowInsecureHttp && HTTP_SCHEME.equals(scheme))) {
            throw new IllegalArgumentException("Webhook URL must use https");
        }
        ssrfGuard.assertPublicHost(uri);
        return uri;
    }
}
