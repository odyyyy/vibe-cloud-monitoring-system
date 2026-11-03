package com.monitoring.healthcheck.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.healthcheck.dto.StepExecutionResult;
import com.monitoring.healthcheck.dto.StepExecutionStatus;
import com.monitoring.healthcheck.dto.SyntheticApiStep;
import com.monitoring.healthcheck.dto.SyntheticAssertion;
import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.entity.HealthCheckType;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.entity.SyntheticHttpMethod;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the workflow engine against a real local HTTP server. {@link SsrfGuard} is mocked so
 * the executor may call loopback; the guard itself is covered by {@link SsrfGuardTest} and the
 * save-time validation by {@link SyntheticCheckDefinitionValidatorTest}.
 */
class MultiStepHealthCheckExecutorTest {

    private SsrfGuard ssrfGuard;
    private SyntheticCheckCodec codec;
    private MultiStepHealthCheckExecutor executor;
    private HttpServer server;
    private int port;
    private final AtomicReference<String> observedAuthorization = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ssrfGuard = mock(SsrfGuard.class);
        when(ssrfGuard.validate(anyString()))
                .thenAnswer(invocation -> URI.create(invocation.getArgument(0)));
        codec = new SyntheticCheckCodec(objectMapper);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        executor = new MultiStepHealthCheckExecutor(
                ssrfGuard, codec, objectMapper, client,
                Duration.ofMillis(500), Duration.ofSeconds(5).toNanos(), 262144);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/login", exchange -> json(exchange, 200,
                "{\"access_token\":\"secret-token\",\"nested\":{\"value\":\"x\"}}"));
        server.createContext("/data", exchange -> {
            observedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            json(exchange, 200, "{\"status\":\"ok\"}");
        });
        server.createContext("/missing-token", exchange -> json(exchange, 200, "{\"ok\":true}"));
        server.createContext("/fail", exchange -> json(exchange, 500, "{\"status\":\"error\"}"));
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            json(exchange, 200, "{}");
        });
        server.createContext("/large", exchange -> json(exchange, 200, "x".repeat(200)));
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private HealthCheckConfig config(List<SyntheticApiStep> steps) {
        return HealthCheckConfig.builder()
                .type(HealthCheckType.MULTI_STEP_API)
                .name("Login flow")
                .stepsJson(codec.writeSteps(steps))
                .method(com.monitoring.healthcheck.entity.HealthCheckMethod.GET)
                .expectedStatusCode(200)
                .intervalSeconds(60)
                .active(true)
                .build();
    }

    private SyntheticApiStep loginStep(String path) {
        Map<String, String> extractions = new LinkedHashMap<>();
        extractions.put("token", "jsonpath:$.access_token");
        return SyntheticApiStep.builder()
                .name("Login")
                .method(SyntheticHttpMethod.POST)
                .url(url(path))
                .headers(new LinkedHashMap<>(Map.of("Content-Type", "application/json")))
                .body("{\"user\":\"demo\"}")
                .extractVariables(extractions)
                .assertions(List.of(SyntheticAssertion.builder()
                        .type("STATUS_CODE")
                        .operator("==")
                        .value(200)
                        .build()))
                .build();
    }

    private SyntheticApiStep dataStep() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer {{token}}");
        return SyntheticApiStep.builder()
                .name("Fetch data")
                .method(SyntheticHttpMethod.GET)
                .url(url("/data"))
                .headers(headers)
                .assertions(List.of(
                        SyntheticAssertion.builder()
                                .type("STATUS_CODE")
                                .operator("==")
                                .value(200)
                                .build(),
                        SyntheticAssertion.builder()
                                .type("JSON_PATH")
                                .operator("==")
                                .path("$.status")
                                .value("ok")
                                .build()))
                .build();
    }

    @Test
    void twoStepWorkflowPassesTokenToSecondStep() {
        HealthCheckOutcome outcome = executor.execute(config(List.of(loginStep("/login"), dataStep())));

        assertEquals(HealthStatus.UP, outcome.status());
        assertNull(outcome.errorMessage());
        assertEquals(2, outcome.stepResults().size());
        StepExecutionResult first = outcome.stepResults().get(0);
        StepExecutionResult second = outcome.stepResults().get(1);
        assertEquals(StepExecutionStatus.PASSED, first.getStatus());
        assertEquals(StepExecutionStatus.PASSED, second.getStatus());
        assertEquals(200, second.getStatusCode());
        assertEquals("Bearer secret-token", observedAuthorization.get());
        assertTrue(outcome.responseTimeMs() >= 0);
        assertFalse(first.getResponseTimeMs() < 0);
    }

    @Test
    void missingExtractionStopsWorkflowAndSkipsRemainingSteps() {
        HealthCheckOutcome outcome = executor.execute(config(List.of(
                loginStep("/missing-token"), dataStep())));

        assertEquals(HealthStatus.DOWN, outcome.status());
        assertNotNull(outcome.errorMessage());
        assertTrue(outcome.errorMessage().contains("could not extract variable 'token'"));
        assertEquals(StepExecutionStatus.FAILED, outcome.stepResults().get(0).getStatus());
        assertEquals(StepExecutionStatus.SKIPPED, outcome.stepResults().get(1).getStatus());
        assertTrue(outcome.stepResults().get(1).getErrorMessage().contains("Skipped"));
    }

    @Test
    void failedAssertionStopsWorkflow() {
        HealthCheckOutcome outcome = executor.execute(config(List.of(
                loginStep("/fail"), dataStep())));

        assertEquals(HealthStatus.DOWN, outcome.status());
        assertTrue(outcome.errorMessage().contains("assertion failed"));
        assertEquals(StepExecutionStatus.FAILED, outcome.stepResults().get(0).getStatus());
        assertEquals(StepExecutionStatus.SKIPPED, outcome.stepResults().get(1).getStatus());
    }

    @Test
    void undefinedVariableFailsStepWithClearMessage() {
        SyntheticApiStep data = dataStep();
        data.getHeaders().put("X-Trace", "{{trace}}");

        HealthCheckOutcome outcome = executor.execute(config(List.of(loginStep("/login"), data)));

        assertEquals(HealthStatus.DOWN, outcome.status());
        assertTrue(outcome.errorMessage().contains("undefined variable"));
        assertEquals(StepExecutionStatus.FAILED, outcome.stepResults().get(1).getStatus());
    }

    @Test
    void slowStepExceedingWorkflowBudgetFails() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        MultiStepHealthCheckExecutor shortBudget = new MultiStepHealthCheckExecutor(
                ssrfGuard, codec, new ObjectMapper(), client,
                Duration.ofSeconds(2), Duration.ofMillis(150).toNanos(), 262144);
        SyntheticApiStep slow = SyntheticApiStep.builder()
                .name("Slow")
                .method(SyntheticHttpMethod.GET)
                .url(url("/slow"))
                .assertions(List.of(SyntheticAssertion.builder()
                        .type("STATUS_CODE")
                        .operator("==")
                        .value(200)
                        .build()))
                .build();

        HealthCheckOutcome outcome = shortBudget.execute(config(List.of(slow)));

        assertEquals(HealthStatus.DOWN, outcome.status());
        assertEquals(StepExecutionStatus.FAILED, outcome.stepResults().get(0).getStatus());
    }

    @Test
    void oversizedResponseBodyFailsWithoutBufferingItAll() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        MultiStepHealthCheckExecutor smallBody = new MultiStepHealthCheckExecutor(
                ssrfGuard, codec, new ObjectMapper(), client,
                Duration.ofSeconds(2), Duration.ofSeconds(5).toNanos(), 10);
        SyntheticApiStep step = SyntheticApiStep.builder()
                .name("Large")
                .method(SyntheticHttpMethod.GET)
                .url(url("/large"))
                .build();

        HealthCheckOutcome outcome = smallBody.execute(config(List.of(step)));

        assertEquals(HealthStatus.DOWN, outcome.status());
        assertTrue(outcome.errorMessage().contains("exceeds"));
    }

    @Test
    void missingWorkflowDefinitionFailsClosed() {
        HealthCheckConfig config = config(List.of());
        config.setStepsJson(null);

        HealthCheckOutcome outcome = executor.execute(config);

        assertEquals(HealthStatus.DOWN, outcome.status());
        assertTrue(outcome.stepResults().isEmpty());
    }
}
