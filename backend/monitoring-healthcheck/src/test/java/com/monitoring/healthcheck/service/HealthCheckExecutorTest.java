package com.monitoring.healthcheck.service;

import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.entity.HealthCheckMethod;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies status mapping of a single check against a real local HTTP server. The SSRF guard is
 * mocked so the executor can talk to loopback while the guard itself is covered by
 * {@link SsrfGuardTest}.
 */
class HealthCheckExecutorTest {

    private SsrfGuard ssrfGuard;
    private HealthCheckExecutor executor;
    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        ssrfGuard = mock(SsrfGuard.class);
        executor = new HealthCheckExecutor(ssrfGuard, 500, 400);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/fail", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private HealthCheckConfig config(String targetUrl, int expectedStatus) {
        return HealthCheckConfig.builder()
                .targetUrl(targetUrl)
                .method(HealthCheckMethod.GET)
                .expectedStatusCode(expectedStatus)
                .intervalSeconds(60)
                .build();
    }

    private void target(String path) {
        when(ssrfGuard.validate(anyString()))
                .thenReturn(URI.create("http://127.0.0.1:" + port + path));
    }

    @Test
    void expectedStatus_returnsUp() {
        target("/ok");

        HealthCheckOutcome outcome = executor.execute(config("http://example.com", 200));

        assertEquals(HealthStatus.UP, outcome.status());
        assertNull(outcome.errorMessage());
    }

    @Test
    void unexpectedStatus_returnsDownWithDetail() {
        target("/fail");

        HealthCheckOutcome outcome = executor.execute(config("http://example.com", 200));

        assertEquals(HealthStatus.DOWN, outcome.status());
        assertNotNull(outcome.errorMessage());
    }

    @Test
    void slowResponse_returnsTimeout() {
        target("/slow");

        HealthCheckOutcome outcome = executor.execute(config("http://example.com", 200));

        assertEquals(HealthStatus.TIMEOUT, outcome.status());
    }

    @Test
    void connectionRefused_returnsDown() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        when(ssrfGuard.validate(anyString()))
                .thenReturn(URI.create("http://127.0.0.1:" + unusedPort + "/ok"));

        HealthCheckOutcome outcome = executor.execute(config("http://example.com", 200));

        assertEquals(HealthStatus.DOWN, outcome.status());
        assertNotNull(outcome.errorMessage());
    }

    @Test
    void blockedTarget_returnsDown() {
        when(ssrfGuard.validate(anyString()))
                .thenThrow(new InvalidTargetUrlException("Target host resolves to a non-public address"));

        HealthCheckOutcome outcome = executor.execute(config("http://example.com", 200));

        assertEquals(HealthStatus.DOWN, outcome.status());
        assertEquals("Target host resolves to a non-public address", outcome.errorMessage());
    }
}
