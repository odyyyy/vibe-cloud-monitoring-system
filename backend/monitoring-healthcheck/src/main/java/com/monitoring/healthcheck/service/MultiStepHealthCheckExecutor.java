package com.monitoring.healthcheck.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.healthcheck.dto.StepExecutionResult;
import com.monitoring.healthcheck.dto.StepExecutionStatus;
import com.monitoring.healthcheck.dto.SyntheticApiStep;
import com.monitoring.healthcheck.dto.SyntheticAssertion;
import com.monitoring.healthcheck.entity.HealthCheckConfig;
import com.monitoring.healthcheck.entity.HealthCheckResult;
import com.monitoring.healthcheck.entity.HealthStatus;
import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes a multi-step API workflow.
 *
 * <p>Steps run sequentially in configuration order. Extracted variables are available to later
 * steps as <code>{{name}}</code> placeholders in the URL, header values and body. The first failed
 * assertion, timeout or extraction error stops the workflow: the check becomes {@code DOWN}, the
 * failing step is recorded with its diagnostic and every remaining step is recorded as
 * {@code SKIPPED}. The total workflow duration is bounded independently of the sum of step
 * timeouts, so one slow chain cannot occupy the scheduler indefinitely.
 *
 * <p>Every resolved URL is validated by {@link SsrfGuard} immediately before the request and
 * redirects are never followed, so a variable cannot redirect a workflow to an internal host.
 * Response bodies are bounded and only used for assertions and extraction; extracted values and
 * response bodies are never persisted.
 */
@Component
public class MultiStepHealthCheckExecutor {

    private static final String USER_AGENT = "vibemonitoring-healthcheck/1.0";
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]{0,63})\\s*}}");
    private static final int MAX_VARIABLE_VALUE_LENGTH = 4096;

    private final SsrfGuard ssrfGuard;
    private final SyntheticCheckCodec codec;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final long totalTimeoutNanos;
    private final int maxResponseBytes;

    @Autowired
    public MultiStepHealthCheckExecutor(
            SsrfGuard ssrfGuard,
            SyntheticCheckCodec codec,
            ObjectMapper objectMapper,
            @Value("${monitoring.healthcheck.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${monitoring.healthcheck.request-timeout-ms:10000}") long requestTimeoutMs,
            @Value("${monitoring.healthcheck.workflow-total-timeout-ms:30000}") long totalTimeoutMs,
            @Value("${monitoring.healthcheck.max-response-bytes:262144}") int maxResponseBytes) {
        this(ssrfGuard, codec, objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                Duration.ofMillis(requestTimeoutMs),
                Duration.ofMillis(totalTimeoutMs).toNanos(),
                maxResponseBytes);
    }

    /** Visible for tests that need a preconfigured client and short timeouts. */
    MultiStepHealthCheckExecutor(
            SsrfGuard ssrfGuard,
            SyntheticCheckCodec codec,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            Duration requestTimeout,
            long totalTimeoutNanos,
            int maxResponseBytes) {
        this.ssrfGuard = ssrfGuard;
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.totalTimeoutNanos = totalTimeoutNanos;
        this.maxResponseBytes = maxResponseBytes;
    }

    public HealthCheckOutcome execute(HealthCheckConfig config) {
        long start = System.nanoTime();
        long deadline = start + totalTimeoutNanos;
        List<SyntheticApiStep> steps = codec.readSteps(config.getStepsJson());
        if (steps.isEmpty()) {
            return new HealthCheckOutcome(
                    HealthStatus.DOWN, elapsedMs(start), "No workflow steps configured", List.of());
        }

        Map<String, String> variables = new HashMap<>();
        List<StepExecutionResult> results = new ArrayList<>(steps.size());
        String failedStepLabel = null;

        for (int index = 0; index < steps.size(); index++) {
            SyntheticApiStep step = steps.get(index);
            String label = "Step " + (index + 1) + " (" + step.getName() + ")";
            if (failedStepLabel != null) {
                results.add(skipped(step, "Skipped because " + failedStepLabel + " failed"));
                continue;
            }
            StepExecutionResult result = executeStep(step, index, variables, deadline);
            results.add(result);
            if (result.getStatus() == StepExecutionStatus.FAILED) {
                failedStepLabel = label;
            }
        }

        int responseTimeMs = elapsedMs(start);
        if (failedStepLabel == null) {
            return new HealthCheckOutcome(HealthStatus.UP, responseTimeMs, null, results);
        }

        StepExecutionResult failed = results.stream()
                .filter(result -> result.getStatus() == StepExecutionStatus.FAILED)
                .findFirst()
                .orElse(null);
        String detail = failed == null || failed.getErrorMessage() == null
                ? "Workflow step failed"
                : failed.getErrorMessage();
        String message = "Workflow failed at " + failedStepLabel + ": " + detail;
        return new HealthCheckOutcome(
                HealthStatus.DOWN, responseTimeMs, truncate(message), results);
    }

    private StepExecutionResult executeStep(
            SyntheticApiStep step,
            int index,
            Map<String, String> variables,
            long deadline) {
        long start = System.nanoTime();
        String label = "Step " + (index + 1) + " (" + step.getName() + ")";

        Resolution url = resolve(step.getUrl(), variables);
        if (!url.missing().isEmpty()) {
            return failed(step, null, start,
                    unresolved(label + " URL", url.missing()));
        }

        URI uri;
        try {
            uri = ssrfGuard.validate(url.value());
        } catch (InvalidTargetUrlException ex) {
            return failed(step, null, start, label + ": " + ex.getMessage());
        }

        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            return failed(step, null, start, "Workflow timed out before " + label + " could run");
        }
        Duration stepTimeout = Duration.ofNanos(Math.min(remaining, requestTimeout.toNanos()));

        HttpRequest request;
        try {
            request = buildRequest(step, uri, variables, stepTimeout);
        } catch (InvalidTargetUrlException ex) {
            return failed(step, null, start, label + ": " + ex.getMessage());
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request,
                    info -> new BoundedStringBodySubscriber(maxResponseBytes));
        } catch (HttpTimeoutException ex) {
            return failed(step, null, start, label + ": request timed out");
        } catch (IOException ex) {
            return failed(step, null, start, label + ": " + describeIo(ex));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failed(step, null, start, label + ": request was interrupted");
        }

        int statusCode = response.statusCode();
        String body = response.body() == null ? "" : response.body();

        String assertionError = evaluateAssertions(step.getAssertions(), statusCode, body);
        if (assertionError != null) {
            return failed(step, statusCode, start, label + ": " + assertionError);
        }

        JsonNode json = null;
        boolean needsJson = step.getExtractVariables() != null
                && step.getExtractVariables().values().stream()
                .anyMatch(expression -> expression != null && expression.startsWith("jsonpath:"));
        if (needsJson) {
            try {
                json = objectMapper.readTree(body);
            } catch (IOException ex) {
                json = null;
            }
            if (json == null) {
                return failed(step, statusCode, start,
                        label + ": response body is not valid JSON for variable extraction");
            }
        }

        for (Map.Entry<String, String> extraction : step.getExtractVariables().entrySet()) {
            Optional<String> extracted = extract(extraction.getValue(), body, json);
            if (extracted.isEmpty()) {
                return failed(step, statusCode, start,
                        label + ": could not extract variable '" + extraction.getKey() + "'");
            }
            String value = extracted.get();
            if (value.length() > MAX_VARIABLE_VALUE_LENGTH) {
                return failed(step, statusCode, start,
                        label + ": extracted variable '" + extraction.getKey() + "' is too long");
            }
            variables.put(extraction.getKey(), value);
        }

        return StepExecutionResult.builder()
                .name(step.getName())
                .status(StepExecutionStatus.PASSED)
                .statusCode(statusCode)
                .responseTimeMs(elapsedMs(start))
                .build();
    }

    private HttpRequest buildRequest(
            SyntheticApiStep step,
            URI uri,
            Map<String, String> variables,
            Duration stepTimeout) {
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
        String body = step.getBody();
        if (body != null && !step.getMethod().forbidsBody()) {
            Resolution resolvedBody = resolve(body, variables);
            if (!resolvedBody.missing().isEmpty()) {
                throw new InvalidTargetUrlException(
                        unresolved("body", resolvedBody.missing()));
            }
            bodyPublisher = HttpRequest.BodyPublishers.ofString(
                    resolvedBody.value(), StandardCharsets.UTF_8);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(stepTimeout)
                .method(step.getMethod().name(), bodyPublisher);

        boolean hasUserAgent = false;
        boolean hasContentType = false;
        if (step.getHeaders() != null) {
            for (Map.Entry<String, String> header : step.getHeaders().entrySet()) {
                Resolution resolved = resolve(header.getValue(), variables);
                if (!resolved.missing().isEmpty()) {
                    throw new InvalidTargetUrlException(
                            unresolved("header '" + header.getKey() + "'", resolved.missing()));
                }
                if (resolved.value() != null
                        && (resolved.value().indexOf('\r') >= 0 || resolved.value().indexOf('\n') >= 0)) {
                    throw new InvalidTargetUrlException(
                            "header '" + header.getKey() + "' contains an invalid value");
                }
                builder.header(header.getKey(), resolved.value());
                String name = header.getKey().toLowerCase(Locale.ROOT);
                hasUserAgent |= "user-agent".equals(name);
                hasContentType |= "content-type".equals(name);
            }
        }
        if (!hasUserAgent) {
            builder.header("User-Agent", USER_AGENT);
        }
        if (body != null && !hasContentType) {
            builder.header("Content-Type", "application/json");
        }
        return builder.build();
    }

    private String evaluateAssertions(
            List<SyntheticAssertion> assertions, int statusCode, String body) {
        if (assertions == null || assertions.isEmpty()) {
            return statusCode >= 200 && statusCode <= 299
                    ? null
                    : "expected a 2xx status but received " + statusCode;
        }
        JsonNode json = null;
        for (SyntheticAssertion assertion : assertions) {
            String type = assertion.getType() == null
                    ? ""
                    : assertion.getType().toUpperCase(Locale.ROOT);
            String operator = assertion.getOperator() == null
                    ? ""
                    : assertion.getOperator().toLowerCase(Locale.ROOT);
            String expected = assertion.getValue() == null ? "" : String.valueOf(assertion.getValue());

            boolean matches;
            switch (type) {
                case "STATUS_CODE" -> {
                    int value;
                    try {
                        value = Integer.parseInt(expected.trim());
                    } catch (NumberFormatException ex) {
                        return "invalid status code assertion";
                    }
                    matches = "==".equals(operator) ? statusCode == value : statusCode != value;
                }
                case "BODY_CONTAINS" -> matches = "contains".equals(operator)
                        ? body.contains(expected)
                        : !body.contains(expected);
                case "JSON_PATH" -> {
                    if (json == null) {
                        try {
                            json = objectMapper.readTree(body);
                        } catch (IOException ex) {
                            return "response body is not valid JSON for assertion " + assertion.describe();
                        }
                    }
                    Optional<JsonNode> actual = SimpleJsonPath.resolve(json, assertion.getPath());
                    if (actual.isEmpty()) {
                        matches = false;
                    } else {
                        String actualText = actual.get().isValueNode()
                                ? actual.get().asText()
                                : actual.get().toString();
                        matches = switch (operator) {
                            case "==" -> actualText.equals(expected);
                            case "!=" -> !actualText.equals(expected);
                            case "contains" -> actualText.contains(expected);
                            case "not_contains" -> !actualText.contains(expected);
                            default -> false;
                        };
                    }
                }
                default -> {
                    return "unsupported assertion type";
                }
            }
            if (!matches) {
                return "assertion failed: " + assertion.describe()
                        + (type.equals("STATUS_CODE") ? " (received " + statusCode + ")" : "");
            }
        }
        return null;
    }

    private Optional<String> extract(String expression, String body, JsonNode json) {
        if (expression == null) {
            return Optional.empty();
        }
        if (expression.startsWith("jsonpath:")) {
            if (json == null) {
                return Optional.empty();
            }
            Optional<JsonNode> node = SimpleJsonPath.resolve(json,
                    expression.substring("jsonpath:".length()));
            if (node.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(node.get().isValueNode()
                    ? node.get().asText()
                    : node.get().toString());
        }
        if (expression.startsWith("regex:")) {
            try {
                Matcher matcher = Pattern.compile(
                        expression.substring("regex:".length())).matcher(body);
                if (!matcher.find()) {
                    return Optional.empty();
                }
                String value = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
                return value == null ? Optional.empty() : Optional.of(value);
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Resolution resolve(String template, Map<String, String> variables) {
        if (template == null) {
            return new Resolution(null, Set.of());
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        Set<String> missing = new LinkedHashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = variables.get(name);
            if (value == null) {
                missing.add(name);
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(result);
        return new Resolution(result.toString(), missing);
    }

    private String unresolved(String where, Set<String> missing) {
        return where + " references undefined variable(s): " + String.join(", ", missing);
    }

    private StepExecutionResult failed(
            SyntheticApiStep step, Integer statusCode, long start, String error) {
        return StepExecutionResult.builder()
                .name(step.getName())
                .status(StepExecutionStatus.FAILED)
                .statusCode(statusCode)
                .responseTimeMs(elapsedMs(start))
                .errorMessage(truncate(error))
                .build();
    }

    private StepExecutionResult skipped(SyntheticApiStep step, String reason) {
        return StepExecutionResult.builder()
                .name(step.getName())
                .status(StepExecutionStatus.SKIPPED)
                .responseTimeMs(0)
                .errorMessage(reason)
                .build();
    }

    private String describeIo(IOException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof BoundedStringBodySubscriber.BodyTooLargeException) {
                return cause.getMessage();
            }
            cause = cause.getCause();
        }
        String detail = ex.getClass().getSimpleName();
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            detail = detail + ": " + ex.getMessage();
        }
        return detail;
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

    private record Resolution(String value, Set<String> missing) {
    }
}
