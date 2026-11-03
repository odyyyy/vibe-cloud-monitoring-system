package com.monitoring.healthcheck.service;

import com.monitoring.healthcheck.dto.SyntheticApiStep;
import com.monitoring.healthcheck.dto.SyntheticAssertion;
import com.monitoring.healthcheck.entity.SyntheticHttpMethod;
import com.monitoring.healthcheck.exception.InvalidTargetUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticCheckDefinitionValidatorTest {

    private static final String PUBLIC_URL = "http://8.8.8.8/health";

    private SyntheticCheckDefinitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SyntheticCheckDefinitionValidator(new SsrfGuard());
    }

    private SyntheticApiStep step(String url) {
        return SyntheticApiStep.builder()
                .name("  Step  ")
                .method(SyntheticHttpMethod.GET)
                .url(url)
                .build();
    }

    @Test
    void acceptsAndNormalizesASafeWorkflow() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer {{token}}");
        Map<String, String> extractions = new LinkedHashMap<>();
        extractions.put("token", "jsonpath:$.access_token");
        SyntheticApiStep step = SyntheticApiStep.builder()
                .name("  Login  ")
                .method(SyntheticHttpMethod.POST)
                .url("  " + PUBLIC_URL + "  ")
                .headers(headers)
                .body("{\"user\":\"demo\"}")
                .extractVariables(extractions)
                .assertions(List.of(SyntheticAssertion.builder()
                        .type("status_code")
                        .operator("==")
                        .value("200")
                        .build()))
                .build();

        List<SyntheticApiStep> normalized = validator.validate(List.of(step));

        assertEquals(1, normalized.size());
        assertEquals("Login", normalized.get(0).getName());
        assertEquals(PUBLIC_URL, normalized.get(0).getUrl());
        assertEquals("STATUS_CODE", normalized.get(0).getAssertions().get(0).getType());
        assertEquals(200, normalized.get(0).getAssertions().get(0).getValue());
    }

    @Test
    void rejectsMoreThanFiveSteps() {
        List<SyntheticApiStep> steps = List.of(
                step(PUBLIC_URL), step(PUBLIC_URL), step(PUBLIC_URL),
                step(PUBLIC_URL), step(PUBLIC_URL), step(PUBLIC_URL));

        InvalidTargetUrlException ex = assertThrows(InvalidTargetUrlException.class,
                () -> validator.validate(steps));
        assertTrue(ex.getMessage().contains("at most 5 steps"));
    }

    @Test
    void rejectsCloudMetadataAddressInAnyStep() {
        InvalidTargetUrlException ex = assertThrows(InvalidTargetUrlException.class,
                () -> validator.validate(List.of(step(PUBLIC_URL), step("http://169.254.169.254/"))));
        assertTrue(ex.getMessage().contains("non-public"));
    }

    @Test
    void rejectsVariablesInHost() {
        InvalidTargetUrlException ex = assertThrows(InvalidTargetUrlException.class,
                () -> validator.validate(List.of(step("http://{{host}}/api"))));
        assertTrue(ex.getMessage().contains("scheme or host"));
    }

    @Test
    void acceptsVariablesInPathAndValidatesLiteralHost() {
        List<SyntheticApiStep> normalized = validator.validate(
                List.of(step("http://8.8.8.8/api/users/{{userId}}")));

        assertEquals("http://8.8.8.8/api/users/{{userId}}", normalized.get(0).getUrl());
    }

    @Test
    void rejectsUnsupportedExtractionExpression() {
        SyntheticApiStep step = step(PUBLIC_URL);
        step.setExtractVariables(Map.of("token", "header:x-token"));

        assertThrows(InvalidTargetUrlException.class, () -> validator.validate(List.of(step)));
    }

    @Test
    void rejectsNestedQuantifierRegex() {
        SyntheticApiStep step = step(PUBLIC_URL);
        step.setExtractVariables(Map.of("token", "regex:(a+)+"));

        assertThrows(InvalidTargetUrlException.class, () -> validator.validate(List.of(step)));
    }

    @Test
    void acceptsSimpleRegexExtraction() {
        SyntheticApiStep step = step(PUBLIC_URL);
        step.setExtractVariables(Map.of("trace", "regex:([a-f0-9]{16})"));

        assertEquals(1, validator.validate(List.of(step)).size());
    }

    @Test
    void rejectsInvalidJsonPath() {
        SyntheticApiStep step = step(PUBLIC_URL);
        step.setExtractVariables(Map.of("token", "jsonpath:$..token"));

        assertThrows(InvalidTargetUrlException.class, () -> validator.validate(List.of(step)));
    }

    @Test
    void rejectsBodyOnGet() {
        SyntheticApiStep step = step(PUBLIC_URL);
        step.setBody("{}");

        assertThrows(InvalidTargetUrlException.class, () -> validator.validate(List.of(step)));
    }

    @Test
    void rejectsHeaderLineBreaks() {
        SyntheticApiStep step = step(PUBLIC_URL);
        step.setHeaders(Map.of("X-Test", "a\r\nInjected: true"));

        assertThrows(InvalidTargetUrlException.class, () -> validator.validate(List.of(step)));
    }

    @Test
    void rejectsForbiddenHeaders() {
        SyntheticApiStep step = step(PUBLIC_URL);
        step.setHeaders(Map.of("Host", "internal.example"));

        assertThrows(InvalidTargetUrlException.class, () -> validator.validate(List.of(step)));
    }

    @Test
    void rejectsInvalidAssertionOperator() {
        SyntheticApiStep step = step(PUBLIC_URL);
        step.setAssertions(List.of(SyntheticAssertion.builder()
                .type("STATUS_CODE")
                .operator(">")
                .value(200)
                .build()));

        assertThrows(InvalidTargetUrlException.class, () -> validator.validate(List.of(step)));
    }
}
