package com.monitoring.alerts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Small bounded HTTP client shared by webhook-based notification senders.
 *
 * <p>Every request has a hard 5-second timeout (configurable), redirects are never followed (so a
 * validated public host cannot bounce the platform to an internal address), and at most 4 KB of
 * the response body is read for diagnostics. Exceptions are converted to results without
 * including the request URL, which may contain a secret path or token.
 */
@Component
public class NotificationHttpClient {

    /** Maximum response bytes read from a notification target. */
    public static final int MAX_RESPONSE_BYTES = 4096;

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public NotificationHttpClient(
            @Value("${monitoring.alerts.notifications.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${monitoring.alerts.notifications.request-timeout-ms:5000}") long requestTimeoutMs) {
        this.requestTimeout = Duration.ofMillis(Math.max(1, requestTimeoutMs));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, connectTimeoutMs)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * POSTs {@code body} with the given headers and returns a bounded delivery result.
     */
    public NotificationSendResult post(URI uri, String body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);

        try {
            HttpResponse<InputStream> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            String snippet = readSnippet(response.body());
            if (status >= 200 && status < 300) {
                return NotificationSendResult.success(status);
            }
            String message = "HTTP " + status + (snippet.isBlank() ? "" : ": " + snippet);
            return NotificationSendResult.httpFailure(status, message);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return NotificationSendResult.networkFailure("Delivery was interrupted");
        } catch (Exception ex) {
            return NotificationSendResult.networkFailure(
                    "Network error (" + ex.getClass().getSimpleName() + ")");
        }
    }

    private String readSnippet(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream) {
            byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES);
            String text = new String(bytes, StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .trim();
            return text.length() <= 300 ? text : text.substring(0, 300);
        } catch (Exception ex) {
            return "";
        }
    }
}
