package com.monitoring.healthcheck.service;

import com.monitoring.healthcheck.dto.SyntheticApiStep;
import com.monitoring.healthcheck.dto.SyntheticAssertion;
import com.monitoring.healthcheck.entity.SyntheticHttpMethod;
import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates and normalizes multi-step API workflow definitions.
 *
 * <p>This is the save-time security boundary: every step URL is checked against {@link SsrfGuard}
 * (with a placeholder substituted for variables so the host is still checked), headers, bodies,
 * extraction expressions and assertions are bounded, and unsupported constructs are rejected.
 * Values from this class are safe to serialize; runtime URLs are re-validated by the executor.
 */
@Component
public class SyntheticCheckDefinitionValidator {

    public static final int MAX_STEPS = 5;
    public static final int MAX_HEADERS_PER_STEP = 20;
    public static final int MAX_EXTRACTIONS_PER_STEP = 10;
    public static final int MAX_ASSERTIONS_PER_STEP = 10;
    public static final int MAX_BODY_LENGTH = 16384;
    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_STEP_NAME_LENGTH = 100;
    public static final int MAX_HEADER_VALUE_LENGTH = 2048;
    public static final int MAX_VARIABLE_NAME_LENGTH = 64;
    public static final int MAX_EXTRACTION_EXPRESSION_LENGTH = 300;
    public static final int MAX_REGEX_LENGTH = 200;
    public static final int MAX_ASSERTION_VALUE_LENGTH = 500;

    private static final Pattern VARIABLE_PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]{0,63})\\s*}}");
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern HEADER_NAME =
            Pattern.compile("[A-Za-z0-9!#$%&'*+.^_|~-]{1,128}");
    private static final Pattern AUTHORITY =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]*)");
    private static final Pattern NESTED_QUANTIFIER =
            Pattern.compile("\\([^)]*[+*][^)]*\\)[+*{]");

    private static final Set<String> FORBIDDEN_HEADERS = Set.of(
            "host", "content-length", "connection", "transfer-encoding", "upgrade");

    private static final Set<String> ASSERTION_TYPES = Set.of(
            "STATUS_CODE", "BODY_CONTAINS", "JSON_PATH");

    private final SsrfGuard ssrfGuard;

    public SyntheticCheckDefinitionValidator(SsrfGuard ssrfGuard) {
        this.ssrfGuard = ssrfGuard;
    }

    /**
     * @return a normalized copy of the definition
     * @throws InvalidTargetUrlException when any part of the definition is unsafe or malformed
     */
    public List<SyntheticApiStep> validate(List<SyntheticApiStep> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new InvalidTargetUrlException("A multi-step check must define at least one step");
        }
        if (steps.size() > MAX_STEPS) {
            throw new InvalidTargetUrlException("A multi-step check supports at most " + MAX_STEPS + " steps");
        }
        List<SyntheticApiStep> normalized = new ArrayList<>(steps.size());
        for (int index = 0; index < steps.size(); index++) {
            normalized.add(validateStep(index, steps.get(index)));
        }
        return normalized;
    }

    private SyntheticApiStep validateStep(int index, SyntheticApiStep step) {
        String label = "Step " + (index + 1);
        if (step == null) {
            throw new InvalidTargetUrlException(label + " is empty");
        }
        String name = step.getName() == null ? "" : step.getName().trim();
        if (name.isEmpty()) {
            throw new InvalidTargetUrlException(label + " must have a name");
        }
        if (name.length() > MAX_STEP_NAME_LENGTH) {
            throw new InvalidTargetUrlException(label + " name must be at most " + MAX_STEP_NAME_LENGTH + " characters");
        }
        if (step.getMethod() == null) {
            throw new InvalidTargetUrlException(label + " must have a method");
        }
        String url = validateUrl(label, step.getUrl());
        String body = step.getBody() == null ? null : step.getBody();
        if (body != null) {
            if (step.getMethod().forbidsBody()) {
                throw new InvalidTargetUrlException(label + " must not define a body for " + step.getMethod());
            }
            if (body.length() > MAX_BODY_LENGTH) {
                throw new InvalidTargetUrlException(label + " body must be at most " + MAX_BODY_LENGTH + " characters");
            }
        }
        return SyntheticApiStep.builder()
                .name(name)
                .method(step.getMethod())
                .url(url)
                .headers(validateHeaders(label, step.getHeaders()))
                .body(body)
                .extractVariables(validateExtractions(label, step.getExtractVariables()))
                .assertions(validateAssertions(label, step.getAssertions()))
                .build();
    }

    private String validateUrl(String label, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidTargetUrlException(label + " URL is required");
        }
        String url = rawUrl.trim();
        if (url.length() > MAX_URL_LENGTH) {
            throw new InvalidTargetUrlException(label + " URL must be at most " + MAX_URL_LENGTH + " characters");
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                throw new InvalidTargetUrlException(label + " URL must not contain whitespace or control characters");
            }
        }
        Matcher authority = AUTHORITY.matcher(url);
        if (!authority.find()) {
            throw new InvalidTargetUrlException(label + " URL must be an absolute http/https URL");
        }
        if (authority.group(1).contains("{{")) {
            throw new InvalidTargetUrlException(label + " URL variables are not allowed in the scheme or host");
        }
        // Validate the concrete shape after substituting variables. The runtime URL is validated
        // again by the executor, so a variable can never smuggle in a different target.
        String candidate = substituteForValidation(url);
        if (candidate.indexOf('{') >= 0 || candidate.indexOf('}') >= 0) {
            throw new InvalidTargetUrlException(label + " URL contains an invalid variable placeholder");
        }
        URI uri = ssrfGuard.validate(candidate);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new InvalidTargetUrlException(label + " URL must use http or https");
        }
        return url;
    }

    private String substituteForValidation(String url) {
        return VARIABLE_PLACEHOLDER.matcher(url).replaceAll("value");
    }

    private Map<String, String> validateHeaders(String label, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (headers.size() > MAX_HEADERS_PER_STEP) {
            throw new InvalidTargetUrlException(
                    label + " supports at most " + MAX_HEADERS_PER_STEP + " headers");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (!HEADER_NAME.matcher(name).matches()) {
                throw new InvalidTargetUrlException(label + " contains an invalid header name");
            }
            if (FORBIDDEN_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                throw new InvalidTargetUrlException(label + " must not set the " + name + " header");
            }
            if (value.length() > MAX_HEADER_VALUE_LENGTH) {
                throw new InvalidTargetUrlException(
                        label + " header value must be at most " + MAX_HEADER_VALUE_LENGTH + " characters");
            }
            if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new InvalidTargetUrlException(label + " header values must not contain line breaks");
            }
            normalized.put(name, value);
        }
        return normalized;
    }

    private Map<String, String> validateExtractions(String label, Map<String, String> extractions) {
        if (extractions == null || extractions.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (extractions.size() > MAX_EXTRACTIONS_PER_STEP) {
            throw new InvalidTargetUrlException(
                    label + " supports at most " + MAX_EXTRACTIONS_PER_STEP + " extractions");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : extractions.entrySet()) {
            String variable = entry.getKey() == null ? "" : entry.getKey().trim();
            if (!VARIABLE_NAME.matcher(variable).matches() || variable.length() > MAX_VARIABLE_NAME_LENGTH) {
                throw new InvalidTargetUrlException(label + " has an invalid extraction variable name");
            }
            String expression = entry.getValue() == null ? "" : entry.getValue().trim();
            validateExtractionExpression(label, expression);
            normalized.put(variable, expression);
        }
        return normalized;
    }

    private void validateExtractionExpression(String label, String expression) {
        if (expression.isEmpty() || expression.length() > MAX_EXTRACTION_EXPRESSION_LENGTH) {
            throw new InvalidTargetUrlException(label + " has an invalid extraction expression");
        }
        if (expression.startsWith("jsonpath:")) {
            String path = expression.substring("jsonpath:".length());
            if (!SimpleJsonPath.isValid(path)) {
                throw new InvalidTargetUrlException(
                        label + " uses an unsupported JSON path; use a simple path like $.access_token");
            }
            return;
        }
        if (expression.startsWith("regex:")) {
            String regex = expression.substring("regex:".length());
            if (regex.isEmpty() || regex.length() > MAX_REGEX_LENGTH) {
                throw new InvalidTargetUrlException(
                        label + " regex must be between 1 and " + MAX_REGEX_LENGTH + " characters");
            }
            if (NESTED_QUANTIFIER.matcher(regex).find()) {
                throw new InvalidTargetUrlException(label + " regex is too complex (nested quantifiers)");
            }
            try {
                Pattern.compile(regex);
            } catch (PatternSyntaxException ex) {
                throw new InvalidTargetUrlException(label + " has an invalid regex");
            }
            return;
        }
        throw new InvalidTargetUrlException(
                label + " extraction must start with jsonpath: or regex:");
    }

    private List<SyntheticAssertion> validateAssertions(String label, List<SyntheticAssertion> assertions) {
        if (assertions == null || assertions.isEmpty()) {
            return List.of();
        }
        if (assertions.size() > MAX_ASSERTIONS_PER_STEP) {
            throw new InvalidTargetUrlException(
                    label + " supports at most " + MAX_ASSERTIONS_PER_STEP + " assertions");
        }
        List<SyntheticAssertion> normalized = new ArrayList<>(assertions.size());
        for (SyntheticAssertion assertion : assertions) {
            normalized.add(validateAssertion(label, assertion));
        }
        return List.copyOf(normalized);
    }

    private SyntheticAssertion validateAssertion(String label, SyntheticAssertion assertion) {
        if (assertion == null) {
            throw new InvalidTargetUrlException(label + " contains an empty assertion");
        }
        String type = assertion.getType() == null
                ? ""
                : assertion.getType().trim().toUpperCase(Locale.ROOT);
        if (!ASSERTION_TYPES.contains(type)) {
            throw new InvalidTargetUrlException(label + " has an unsupported assertion type");
        }
        String operator = assertion.getOperator() == null
                ? ""
                : assertion.getOperator().trim().toLowerCase(Locale.ROOT);
        Object value = assertion.getValue();
        String path = assertion.getPath() == null ? null : assertion.getPath().trim();

        switch (type) {
            case "STATUS_CODE" -> {
                requireOperator(label, operator, Set.of("==", "!="));
                int status = parseStatusCode(label, value);
                value = status;
            }
            case "BODY_CONTAINS" -> {
                requireOperator(label, operator, Set.of("contains", "not_contains"));
                value = requireStringValue(label, value);
            }
            case "JSON_PATH" -> {
                requireOperator(label, operator, Set.of("==", "!=", "contains", "not_contains"));
                if (path == null || !SimpleJsonPath.isValid(path)) {
                    throw new InvalidTargetUrlException(
                            label + " JSON path assertion needs a simple path like $.status");
                }
                value = requireStringValue(label, value);
            }
            default -> throw new InvalidTargetUrlException(label + " has an unsupported assertion type");
        }
        return SyntheticAssertion.builder()
                .type(type)
                .operator(operator)
                .value(value)
                .path("JSON_PATH".equals(type) ? path : null)
                .build();
    }

    private void requireOperator(String label, String operator, Set<String> allowed) {
        if (!allowed.contains(operator)) {
            throw new InvalidTargetUrlException(label + " has an unsupported assertion operator");
        }
    }

    private int parseStatusCode(String label, Object value) {
        int status;
        if (value instanceof Number number) {
            status = number.intValue();
        } else {
            try {
                status = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ex) {
                throw new InvalidTargetUrlException(label + " status code assertion needs a number");
            }
        }
        if (status < 100 || status > 599) {
            throw new InvalidTargetUrlException(label + " status code assertion must be between 100 and 599");
        }
        return status;
    }

    private String requireStringValue(String label, Object value) {
        if (value == null) {
            throw new InvalidTargetUrlException(label + " assertion needs a value");
        }
        String text = String.valueOf(value);
        if (text.length() > MAX_ASSERTION_VALUE_LENGTH) {
            throw new InvalidTargetUrlException(
                    label + " assertion value must be at most " + MAX_ASSERTION_VALUE_LENGTH + " characters");
        }
        return text;
    }
}
