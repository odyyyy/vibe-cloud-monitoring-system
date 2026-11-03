package com.monitoring.collector.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.collector.entity.LogPipelineAction;
import com.monitoring.collector.entity.LogPipelineRule;

import java.util.List;

/** Public entry point for rule validation and response metadata. */
public final class LogPipelineRuleSupport {

    private LogPipelineRuleSupport() {
    }

    /**
     * Validates a submitted rule and returns the canonical action config plus the fields a
     * PARSE_REGEX rule extracts. Throws {@code InvalidLogPipelineRuleException} on bad input.
     */
    public static RuleMetadata validate(LogPipelineAction action,
                                        String filter,
                                        JsonNode actionConfig,
                                        ObjectMapper objectMapper) {
        CompiledLogPipelineRule compiled = LogPipelineRuleCompiler.compile(
                null, null, filter, action, actionConfig, objectMapper);
        return new RuleMetadata(compiled.canonicalConfig(), compiled.extractedFields());
    }

    public static RuleMetadata metadata(LogPipelineRule rule, ObjectMapper objectMapper) {
        CompiledLogPipelineRule compiled = LogPipelineRuleCompiler.compile(rule, objectMapper);
        return new RuleMetadata(compiled.canonicalConfig(), compiled.extractedFields());
    }

    public record RuleMetadata(String canonicalConfig, List<String> extractedFields) {
        public RuleMetadata {
            extractedFields = extractedFields == null ? List.of() : List.copyOf(extractedFields);
        }
    }
}
