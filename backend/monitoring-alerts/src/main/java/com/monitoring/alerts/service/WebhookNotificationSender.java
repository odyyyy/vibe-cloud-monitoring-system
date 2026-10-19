package com.monitoring.alerts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.alerts.dto.response.AlertNotificationPayload;
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
import java.util.Locale;

/**
 * Delivers alert notifications as a JSON POST to a customer's HTTPS webhook.
 *
 * <p>The URL is re-validated (https, public host) immediately before each request to reduce the
 * DNS-rebinding window, and redirects are disabled so a validated host cannot bounce the request
 * to an internal address.
 */
@Component
@Slf4j
public class WebhookNotificationSender implements NotificationSender {

    private static final String USER_AGENT = "vibemonitoring-alerts/1.0";
    private static final String HTTPS_SCHEME = "https";

    private final SsrfGuard ssrfGuard;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public WebhookNotificationSender(
            SsrfGuard ssrfGuard,
            ObjectMapper objectMapper,
            @Value("${monitoring.alerts.webhook.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${monitoring.alerts.webhook.request-timeout-ms:10000}") long requestTimeoutMs) {
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
        return NotificationChannelType.WEBHOOK;
    }

    @Override
    public void deliver(NotificationChannel channel, AlertEvent event) {
        URI uri = validate(channel.getUrl());
        String body;
        try {
            body = objectMapper.writeValueAsString(AlertNotificationPayload.from(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize alert notification", ex);
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
                log.warn("Webhook channel {} responded with status {}", channel.getId(), response.statusCode());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Webhook delivery was interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Webhook delivery failed: " + ex.getMessage(), ex);
        }
    }

    private URI validate(String rawUrl) {
        URI uri = ssrfGuard.parse(rawUrl);
        if (!HTTPS_SCHEME.equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Webhook URL must use https");
        }
        ssrfGuard.assertPublicHost(uri);
        return uri;
    }
}
