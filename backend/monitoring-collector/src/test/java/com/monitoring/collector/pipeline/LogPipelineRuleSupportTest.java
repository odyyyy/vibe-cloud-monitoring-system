package com.monitoring.collector.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.collector.entity.LogPipelineAction;
import com.monitoring.collector.exception.InvalidLogPipelineRuleException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the rule contract shared by the REST API and the agent before persistence. */
class LogPipelineRuleSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsUnderscoredGroupNamesFromARe2CompatiblePattern() {
        LogPipelineRuleSupport.RuleMetadata metadata = validate(
                LogPipelineAction.PARSE_REGEX,
                "*",
                "{\"pattern\":\"order (?P<order_id>\\\\d+) for (?P<user_id>\\\\d+)\"}");

        assertEquals(List.of("order_id", "user_id"), metadata.extractedFields());
        assertTrue(metadata.canonicalConfig().contains("order_id"));
    }

    @Test
    void rejectsLookbehindBackreferenceAndPossessiveQuantifiers() {
        assertInvalid(LogPipelineAction.PARSE_REGEX, "{\"pattern\":\"(?<=a)(?P<x>b)\"}");
        assertInvalid(LogPipelineAction.PARSE_REGEX, "{\"pattern\":\"(a)\\\\1\"}");
        assertInvalid(LogPipelineAction.PARSE_REGEX, "{\"pattern\":\"a++(?P<x>b)\"}");
        assertInvalid(LogPipelineAction.PARSE_REGEX, "{\"pattern\":\"\\\\Qliteral\\\\E(?P<x>b)\"}");
    }

    @Test
    void requiresAtLeastOneNamedCaptureGroupForParsing() {
        assertInvalid(LogPipelineAction.PARSE_REGEX, "{\"pattern\":\"(order)\"}");
    }

    @Test
    void rejectsUnknownActionConfigFields() {
        assertInvalid(LogPipelineAction.DROP, "{\"pattern\":\"x\"}");
        assertInvalid(LogPipelineAction.ADD_ATTRIBUTE, "{\"key\":\"env\",\"value\":\"prod\",\"extra\":1}");
    }

    @Test
    void rejectsReservedAgentContextKeys() {
        assertInvalid(LogPipelineAction.ADD_ATTRIBUTE, "{\"key\":\"service\",\"value\":\"forged\"}");
        assertInvalid(LogPipelineAction.PARSE_REGEX,
                "{\"pattern\":\"(?P<host>.*)\"}");
        assertInvalid(LogPipelineAction.MASK,
                "{\"pattern\":\"x\",\"target\":\"context\",\"field\":\"k8s.namespace\"}");
    }

    @Test
    void acceptsBothCasesForTheMaskTarget() {
        LogPipelineRuleSupport.RuleMetadata metadata = validate(
                LogPipelineAction.MASK,
                "*",
                "{\"pattern\":\"x\",\"target\":\"context\",\"field\":\"token\"}");

        assertTrue(metadata.canonicalConfig().contains("token"));
    }

    @Test
    void rejectsAContextFieldForAMessageMask() {
        assertInvalid(LogPipelineAction.MASK,
                "{\"pattern\":\"x\",\"target\":\"message\",\"field\":\"token\"}");
    }

    @Test
    void rejectsPatternsLongerThanTheLimit() {
        String longPattern = "a".repeat(SafeRegex.MAX_PATTERN_LENGTH + 1);
        assertInvalid(LogPipelineAction.PARSE_REGEX,
                "{\"pattern\":\"" + longPattern + "\"}");
    }

    private LogPipelineRuleSupport.RuleMetadata validate(LogPipelineAction action,
                                                         String filter,
                                                         String config) {
        try {
            JsonNode node = objectMapper.readTree(config);
            return LogPipelineRuleSupport.validate(action, filter, node, objectMapper);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void assertInvalid(LogPipelineAction action, String config) {
        assertThrows(InvalidLogPipelineRuleException.class,
                () -> validate(action, "*", config));
    }
}
