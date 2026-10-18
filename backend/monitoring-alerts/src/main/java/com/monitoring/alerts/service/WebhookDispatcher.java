package com.monitoring.alerts.service;

import com.monitoring.alerts.event.AlertEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.alerts.dto.response.AlertNotificationPayload;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import com.monitoring.healthcheck.service.SsrfGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Delivers alert notifications to a tenant's webhook channels.
 *
 * <p>Delivery is asynchronous and bounded: {@link #dispatch} only enqueues work on a dedicated
 * bounded executor, so a slow or hostile endpoint can never block evaluation or exhaust threads.
 * The URL is re-validated (https, public host) immediately before each request to reduce the
 * DNS-rebinding window, and redirects are disabled so a validated host cannot bounce the request
 * to an internal address. Failures are logged and never propagated.
 */
@Component
@Slf4j
public class WebhookDispatcher {

    private static final String USER_AGENT = "vibemonitoring-alerts/1.0";
    private static final String HTTPS_SCHEME = "https";

    private final NotificationChannelRepository channelRepository;
    private final SsrfGuard ssrfGuard;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ExecutorService webhookExecutor;

    public WebhookDispatcher(
            NotificationChannelRepository channelRepository,
            SsrfGuard ssrfGuard,
            ObjectMapper objectMapper,
            @Qualifier("alertWebhookExecutor") ExecutorService alertWebhookExecutor,
            @Value("${monitoring.alerts.webhook.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${monitoring.alerts.webhook.request-timeout-ms:10000}") long requestTimeoutMs) {
        this.channelRepository = channelRepository;
        this.ssrfGuard = ssrfGuard;
        this.objectMapper = objectMapper;
        this.webhookExecutor = alertWebhookExecutor;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Queues a notification for every active channel of the event's owner. Safe to call after a
     * transaction has committed; it performs a read and then hands delivery to the executor.
     */
    public void dispatch(AlertEvent event) {
        List<NotificationChannel> channels = channelRepository.findByOrganizationIdAndActiveTrue(event.organizationId());
        if (channels.isEmpty()) {
            return;
        }
        AlertNotificationPayload payload = AlertNotificationPayload.from(event);
        for (NotificationChannel channel : channels) {
            webhookExecutor.submit(() -> deliver(channel, payload));
        }
    }

    private void deliver(NotificationChannel channel, AlertNotificationPayload payload) {
        try {
            URI uri = validate(channel.getUrl());
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                log.warn("Webhook channel {} responded with status {}", channel.getId(), response.statusCode());
            }
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize alert notification for channel {}: {}", channel.getId(), ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            // A dead endpoint or blocked URL must not affect other deliveries.
            log.warn("Webhook delivery to channel {} failed: {}", channel.getId(), ex.getMessage());
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
