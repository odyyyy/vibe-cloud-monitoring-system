package com.monitoring.collector.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.collector.dto.request.LogEventPayload;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.entity.LogPipelineAction;
import com.monitoring.collector.entity.LogPipelineRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executes the same engine used by ingestion and the test endpoint, so the assertions describe
 * the behavior an agent and a direct API sender must both reproduce.
 */
class LogPipelineEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LogPipelineEngine engine = new LogPipelineEngine(objectMapper);

    @Test
    void dropsMatchingEventsAndStopsProcessing() {
        LogPipelineEngine.PipelineResult result = engine.apply(
                LogLevel.DEBUG, "cache miss", "{\"service\":\"api\"}",
                List.of(rule("drop debug", "level:DEBUG", LogPipelineAction.DROP, "{}"),
                        rule("never reached", "*", LogPipelineAction.ADD_ATTRIBUTE,
                                "{\"key\":\"reached\",\"value\":\"yes\"}")));

        assertTrue(result.dropped());
        assertEquals(1, result.matchedRules().size());
        assertEquals("drop debug", result.matchedRules().get(0).name());
    }

    @Test
    void keepsNonMatchingEventsUnchanged() {
        LogPipelineEngine.PipelineResult result = engine.apply(
                LogLevel.INFO, "started", "{\"service\":\"api\"}",
                List.of(rule("drop debug", "level:DEBUG", LogPipelineAction.DROP, "{}")));

        assertFalse(result.dropped());
        assertEquals("started", result.message());
        assertEquals("{\"service\":\"api\"}", result.context());
        assertTrue(result.matchedRules().isEmpty());
    }

    @Test
    void parsesNamedGroupsIntoTheContextDocument() throws Exception {
        LogPipelineEngine.PipelineResult result = engine.apply(
                LogLevel.WARN, "order 12345 failed", "{\"service\":\"checkout\"}",
                List.of(rule("extract order", "*", LogPipelineAction.PARSE_REGEX,
                        "{\"pattern\":\"order (?P<order_id>\\\\d+) failed\"}")));

        assertEquals("order 12345 failed", result.message());
        JsonNode context = objectMapper.readTree(result.context());
        assertEquals("checkout", context.get("service").asText());
        assertEquals("12345", context.get("order_id").asText());
        assertEquals(1, result.matchedRules().size());
        assertEquals(LogPipelineAction.PARSE_REGEX, result.matchedRules().get(0).action());
    }

    @Test
    void appliesParseAddAttributeAndMaskInOrder() throws Exception {
        List<LogPipelineRule> rules = List.of(
                rule("parse", "level:ERROR", LogPipelineAction.PARSE_REGEX,
                        "{\"pattern\":\"user=(?P<user_id>\\\\d+)\"}"),
                rule("env", "level:ERROR", LogPipelineAction.ADD_ATTRIBUTE,
                        "{\"key\":\"environment\",\"value\":\"prod\"}"),
                rule("mask card", "level:ERROR", LogPipelineAction.MASK,
                        "{\"pattern\":\"\\\\d{4} \\\\d{4}\",\"target\":\"message\"}"));

        LogPipelineEngine.PipelineResult result = engine.apply(
                LogLevel.ERROR, "user=42 card 1234 5678", "{}", rules);

        assertEquals("user=42 card [REDACTED]", result.message());
        JsonNode context = objectMapper.readTree(result.context());
        assertEquals("42", context.get("user_id").asText());
        assertEquals("prod", context.get("environment").asText());
        assertEquals(3, result.matchedRules().size());
    }

    @Test
    void masksAContextField() throws Exception {
        LogPipelineEngine.PipelineResult result = engine.apply(
                LogLevel.INFO, "payment", "{\"card_number\":\"1234-5678\"}",
                List.of(rule("mask card", "*", LogPipelineAction.MASK,
                        "{\"pattern\":\"\\\\d{4}-\\\\d{4}\",\"target\":\"CONTEXT\","
                                + "\"field\":\"card_number\"}")));

        JsonNode context = objectMapper.readTree(result.context());
        assertEquals("[REDACTED]", context.get("card_number").asText());
    }

    @Test
    void filtersOnContextFieldsAndMessageCaseInsensitively() {
        List<LogPipelineRule> rules = List.of(
                rule("healthcheck", "message:\"HEALTHCHECK\"", LogPipelineAction.DROP, "{}"),
                rule("api only", "context.component:api&&level:DEBUG", LogPipelineAction.DROP, "{}"));
        String context = "{\"component\":\"api\"}";

        assertTrue(engine.apply(LogLevel.INFO, "healthcheck ok", context, rules).dropped());
        assertTrue(engine.apply(LogLevel.DEBUG, "cache miss", context, rules).dropped());
        assertFalse(engine.apply(LogLevel.INFO, "cache miss", context, rules).dropped());
    }

    @Test
    void preservesMalformedContextButStillMasksTheMessage() {
        LogPipelineEngine.PipelineResult result = engine.apply(
                LogLevel.ERROR, "secret 1234", "not-json",
                List.of(rule("mask", "*", LogPipelineAction.MASK,
                                "{\"pattern\":\"\\\\d{4}\",\"target\":\"MESSAGE\"}"),
                        rule("add", "*", LogPipelineAction.ADD_ATTRIBUTE,
                                "{\"key\":\"env\",\"value\":\"prod\"}")));

        assertEquals("secret [REDACTED]", result.message());
        assertEquals("not-json", result.context());
    }

    @Test
    void skipsAnInvalidStoredRuleWithoutLosingTheEvent() {
        LogPipelineEngine.PipelineResult result = engine.apply(
                LogLevel.DEBUG, "cache miss", "{}",
                List.of(rule("broken", "*", LogPipelineAction.PARSE_REGEX, "{\"pattern\":\"(\"}"),
                        rule("drop debug", "level:DEBUG", LogPipelineAction.DROP, "{}")));

        assertTrue(result.dropped());
        assertEquals(1, result.matchedRules().size());
        assertEquals("drop debug", result.matchedRules().get(0).name());
    }

    @Test
    void appliesTheSameRulesToEveryEventInABatch() {
        List<LogPipelineRule> rules = List.of(
                rule("drop debug", "level:DEBUG", LogPipelineAction.DROP, "{}"),
                rule("env", "*", LogPipelineAction.ADD_ATTRIBUTE,
                        "{\"key\":\"environment\",\"value\":\"prod\"}"));

        List<LogPipelineEngine.PipelineResult> results = engine.applyBatch(
                List.of(payload(LogLevel.DEBUG, "cache miss"),
                        payload(LogLevel.INFO, "started")),
                rules);

        assertEquals(2, results.size());
        assertTrue(results.get(0).dropped());
        assertEquals(1, results.get(0).matchedRules().size());
        assertFalse(results.get(1).dropped());
        assertEquals(1, results.get(1).matchedRules().size());
    }

    @Test
    void addAttributeCannotOverwriteAReservedAgentKey() {
        LogPipelineEngine.PipelineResult result = engine.apply(
                LogLevel.INFO, "started", "{\"host\":\"server-1\"}",
                List.of(rule("bad host", "*", LogPipelineAction.ADD_ATTRIBUTE,
                        "{\"key\":\"host\",\"value\":\"forged\"}")));

        assertTrue(result.matchedRules().isEmpty());
        assertEquals("{\"host\":\"server-1\"}", result.context());
    }

    private static LogPipelineRule rule(String name,
                                        String filter,
                                        LogPipelineAction action,
                                        String config) {
        return LogPipelineRule.builder()
                .name(name)
                .filterExpression(filter)
                .action(action)
                .actionConfig(config)
                .build();
    }

    private static LogEventPayload payload(LogLevel level, String message) {
        return LogEventPayload.builder()
                .level(level)
                .message(message)
                .context("{}")
                .timestamp(Instant.parse("2026-10-09T00:00:00Z"))
                .build();
    }
}
