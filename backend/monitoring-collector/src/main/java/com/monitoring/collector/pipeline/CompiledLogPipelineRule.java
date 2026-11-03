package com.monitoring.collector.pipeline;

import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.entity.LogPipelineAction;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A validated, compiled pipeline rule. Immutable and safe to reuse across a batch. */
final class CompiledLogPipelineRule {

    private final Long id;
    private final String name;
    private final LogPipelineAction action;
    private final LogPipelineFilter filter;
    private final SafeRegex regex;
    private final String attributeKey;
    private final String attributeValue;
    private final String maskTarget;
    private final String maskField;
    private final String canonicalConfig;
    private final List<String> extractedFields;

    CompiledLogPipelineRule(Long id,
                            String name,
                            LogPipelineAction action,
                            LogPipelineFilter filter,
                            SafeRegex regex,
                            String attributeKey,
                            String attributeValue,
                            String maskTarget,
                            String maskField,
                            String canonicalConfig,
                            List<String> extractedFields) {
        this.id = id;
        this.name = name;
        this.action = action;
        this.filter = filter;
        this.regex = regex;
        this.attributeKey = attributeKey;
        this.attributeValue = attributeValue;
        this.maskTarget = maskTarget;
        this.maskField = maskField;
        this.canonicalConfig = canonicalConfig;
        this.extractedFields = extractedFields == null ? List.of() : List.copyOf(extractedFields);
    }

    Long id() {
        return id;
    }

    String name() {
        return name;
    }

    LogPipelineAction action() {
        return action;
    }

    String canonicalConfig() {
        return canonicalConfig;
    }

    List<String> extractedFields() {
        return extractedFields;
    }

    boolean matches(LogLevel level, String message, LogPipelineContext context) {
        return filter.matches(level, message, context);
    }

    Map<String, String> parse(String message) {
        return regex.findNamedGroups(message);
    }

    String replaceAll(String value) {
        return regex.replaceAll(value);
    }

    SafeRegex regex() {
        return regex;
    }

    boolean isDrop() {
        return action == LogPipelineAction.DROP;
    }

    boolean isParse() {
        return action == LogPipelineAction.PARSE_REGEX;
    }

    boolean isAddAttribute() {
        return action == LogPipelineAction.ADD_ATTRIBUTE;
    }

    boolean isMask() {
        return action == LogPipelineAction.MASK;
    }

    String attributeKey() {
        return attributeKey;
    }

    String attributeValue() {
        return attributeValue;
    }

    boolean masksMessage() {
        return "MESSAGE".equals(maskTarget);
    }

    String maskField() {
        return maskField;
    }

    Set<String> extractedFieldSet() {
        return Set.copyOf(extractedFields);
    }
}
