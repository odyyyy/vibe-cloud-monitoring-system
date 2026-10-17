package com.monitoring.healthcheck.service;

import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.entity.HealthCheckResult;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Performs a single HTTP synthetic check against a validated target.
 *
 * <p>Redirects are disabled (a redirect to an internal address would defeat the SSRF guard),
 * connect and request timeouts are bounded, and request/response bodies are discarded. The
 * target URL is re-validated against {@link SsrfGuard} right before the request.
 *
 * <p>Residual DNS-rebinding risk: {@code java.net.http.HttpClient} resolves the host itself and
 * offers no hook to pin the validated address, so a host could in theory answer with a public
 * address during validation and a private one at connect time. Validating immediately before
 * the request (which primes the JVM DNS cache with the validated answer) and never following
 * redirects keeps this window as small as the JDK allows; see {@link SsrfGuard} for the details.
 */
@Component
public class HealthCheckExecutor {

    private static final String USER_AGENT = "vibemonitoring-healthcheck/1.0";

    private final SsrfGuard ssrfGuard;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public HealthCheckExecutor(
            SsrfGuard ssrfGuard,
            @Value("${monitoring.healthcheck.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${monitoring.healthcheck.request-timeout-ms:10000}") long requestTimeoutMs) {
        this.ssrfGuard = ssrfGuard;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public HealthCheckOutcome execute(HealthCheckConfig config) {
        long start = System.nanoTime();
        try {
            URI uri = ssrfGuard.validate(config.getTargetUrl());
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout)
                    .method(config.getMethod().name(), HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", USER_AGENT)
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int elapsed = elapsedMs(start);
            if (response.statusCode() == config.getExpectedStatusCode()) {
                return new HealthCheckOutcome(HealthStatus.UP, elapsed, null);
            }
            return new HealthCheckOutcome(
                    HealthStatus.DOWN,
                    elapsed,
                    "Expected status " + config.getExpectedStatusCode()
                            + " but received " + response.statusCode());
        } catch (InvalidTargetUrlException ex) {
            return new HealthCheckOutcome(HealthStatus.DOWN, elapsedMs(start), truncate(ex.getMessage()));
        } catch (HttpTimeoutException ex) {
            return new HealthCheckOutcome(HealthStatus.TIMEOUT, elapsedMs(start), "Request timed out");
        } catch (IOException ex) {
            String detail = ex.getClass().getSimpleName();
            if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
                detail = detail + ": " + ex.getMessage();
            }
            return new HealthCheckOutcome(HealthStatus.DOWN, elapsedMs(start), truncate(detail));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new HealthCheckOutcome(HealthStatus.DOWN, elapsedMs(start), "Check was interrupted");
        }
    }

    private int elapsedMs(long startNanos) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= HealthCheckResult.MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, HealthCheckResult.MAX_ERROR_MESSAGE_LENGTH);
    }
}
