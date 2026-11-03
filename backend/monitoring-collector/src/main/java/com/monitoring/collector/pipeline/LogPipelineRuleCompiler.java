package com.monitoring.collector.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.collector.entity.LogPipelineAction;
import com.monitoring.collector.entity.LogPipelineRule;
import com.monitoring.collector.exception.InvalidLogPipelineRuleException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates persisted or submitted rule definitions and compiles their regexes once. */
final class LogPipelineRuleCompiler {

    static final int MAX_PATTERN_LENGTH = SafeRegex.MAX_PATTERN_LENGTH;
    static final int MAX_ATTRIBUTE_VALUE_LENGTH = 256;
    static final int MAX_ATTRIBUTE_KEY_LENGTH = 64;

    private static final Pattern CONTEXT_KEY =
            Pattern.compile("[A-Za-z0-9_.-]{1," + MAX_ATTRIBUTE_KEY_LENGTH + "}");
    private static final Set<String> RESERVED_CONTEXT_KEYS = Set.of(
            "service", "source", "host",
            "k8s.pod.name", "k8s.namespace", "k8s.container.name");

    private LogPipelineRuleCompiler() {
    }

    static CompiledLogPipelineRule compile(LogPipelineRule rule, ObjectMapper objectMapper) {
        JsonNode config = null;
        if (rule.getActionConfig() != null && !rule.getActionConfig().isBlank()) {
            try {
                config = objectMapper.readTree(rule.getActionConfig());
            } catch (Exception e) {
                throw invalid("Stored actionConfig is not valid JSON");
            }
        }
        return compile(rule.getId(), rule.getName(), rule.getFilterExpression(),
                rule.getAction(), config, objectMapper);
    }

    static CompiledLogPipelineRule compile(Long id,
                                           String name,
                                           String filterExpression,
                                           LogPipelineAction action,
                                           JsonNode config,
                                           ObjectMapper objectMapper) {
        if (action == null) {
            throw invalid("action is required");
        }
        LogPipelineFilter filter = LogPipelineFilter.parse(filterExpression);
        String canonicalConfig = "{}";
        SafeRegex regex = null;
        String key = null;
        String value = null;
        String maskTarget = null;
        String maskField = null;
        List<String> extractedFields = List.of();

        switch (action) {
            case DROP -> {
                requireOnlyFields(config, Set.of());
                canonicalConfig = "{}";
            }
            case PARSE_REGEX -> {
                requireOnlyFields(config, Set.of("pattern"));
                String pattern = requiredText(config, "pattern");
                validateRe2Compatible(pattern);
                regex = compileRegex(pattern);
                extractedFields = regex.namedGroupNames();
                if (extractedFields.isEmpty()) {
                    throw invalid("PARSE_REGEX requires at least one named capture group");
                }
                for (String field : extractedFields) {
                    validateContextKey(field, "capture group");
                }
                canonicalConfig = writeConfig(objectMapper, config);
            }
            case ADD_ATTRIBUTE -> {
                requireOnlyFields(config, Set.of("key", "value"));
                key = requiredText(config, "key");
                value = requiredText(config, "value", MAX_ATTRIBUTE_VALUE_LENGTH);
                validateContextKey(key, "attribute");
                canonicalConfig = writeConfig(objectMapper, config);
            }
            case MASK -> {
                requireOnlyFields(config, Set.of("pattern", "target", "field"));
                String pattern = requiredText(config, "pattern");
                validateRe2Compatible(pattern);
                regex = compileRegex(pattern);
                String target = optionalText(config, "target");
                maskTarget = target == null ? "MESSAGE" : target.toUpperCase(Locale.ROOT);
                if (!maskTarget.equals("MESSAGE") && !maskTarget.equals("CONTEXT")) {
                    throw invalid("MASK target must be 'message' or 'context'");
                }
                if (maskTarget.equals("CONTEXT")) {
                    maskField = requiredText(config, "field");
                    validateContextKey(maskField, "context field");
                } else if (optionalText(config, "field") != null) {
                    throw invalid("MASK field is only valid when target is 'context'");
                }
                canonicalConfig = writeConfig(objectMapper, config);
            }
            default -> throw invalid("Unsupported action");
        }

        return new CompiledLogPipelineRule(
                id, name, action, filter, regex, key, value,
                maskTarget, maskField, canonicalConfig, extractedFields);
    }

    static List<String> extractedFields(String filterExpression,
                                        LogPipelineAction action,
                                        JsonNode config,
                                        ObjectMapper objectMapper) {
        if (action != LogPipelineAction.PARSE_REGEX) {
            return List.of();
        }
        return compile(null, null, filterExpression, action, config, objectMapper).extractedFields();
    }

    private static SafeRegex compileRegex(String pattern) {
        try {
            return SafeRegex.compile(pattern);
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage());
        }
    }

    private static void validateContextKey(String key, String kind) {
        if (key == null || !CONTEXT_KEY.matcher(key).matches()) {
            throw invalid("Invalid " + kind + " key; use letters, digits, dots, dashes and underscores");
        }
        if (RESERVED_CONTEXT_KEYS.contains(key)) {
            throw invalid("The context key '" + key + "' is reserved by the agent");
        }
    }

    /**
     * Rejects constructs that Java supports but Go's RE2 engine does not. Rules must behave
     * identically in the agent and in the backend fallback.
     */
    private static void validateRe2Compatible(String pattern) {
        if (pattern.contains("(?<=") || pattern.contains("(?<!") || pattern.contains("(?>")
                || pattern.contains("(?(") || pattern.contains("\\Q") || pattern.contains("\\E")
                || pattern.contains("\\G") || pattern.contains("\\Z") || pattern.contains("\\R")
                || pattern.contains("\\k<")) {
            throw invalid("pattern uses a construct that is not supported by the agent's RE2 engine");
        }

        boolean inClass = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\' && i + 1 < pattern.length()) {
                char next = pattern.charAt(i + 1);
                if (!inClass && next >= '1' && next <= '9') {
                    throw invalid("backreferences are not supported by the agent's RE2 engine");
                }
                i++;
                continue;
            }
            if (c == '[') {
                inClass = true;
            } else if (c == ']') {
                inClass = false;
            } else if (!inClass && (c == '*' || c == '+' || c == '?')
                    && i + 1 < pattern.length() && pattern.charAt(i + 1) == '+') {
                throw invalid("possessive quantifiers are not supported by the agent's RE2 engine");
            }
        }
    }

    private static void requireOnlyFields(JsonNode config, Set<String> allowed) {
        if (config == null || config.isNull()) {
            if (allowed.isEmpty()) {
                return;
            }
            throw invalid("actionConfig is required for this action");
        }
        if (!config.isObject()) {
            throw invalid("actionConfig must be a JSON object");
        }
        Iterator<String> fields = config.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw invalid("Unknown actionConfig field '" + field + "'");
            }
        }
    }

    private static String requiredText(JsonNode config, String field) {
        return requiredText(config, field, MAX_PATTERN_LENGTH + 1);
    }

    private static String requiredText(JsonNode config, String field, int maxLength) {
        String value = optionalText(config, field);
        if (value == null || value.isBlank()) {
            throw invalid("actionConfig." + field + " is required");
        }
        if (value.length() > maxLength) {
            throw invalid("actionConfig." + field + " is too long");
        }
        return value;
    }

    private static String optionalText(JsonNode config, String field) {
        if (config == null || !config.isObject()) {
            return null;
        }
        JsonNode node = config.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw invalid("actionConfig." + field + " must be a string");
        }
        return node.asText();
    }

    private static String writeConfig(ObjectMapper objectMapper, JsonNode config) {
        try {
            return objectMapper.writeValueAsString(config == null ? objectMapper.createObjectNode() : config);
        } catch (Exception e) {
            throw invalid("actionConfig could not be serialized");
        }
    }

    private static InvalidLogPipelineRuleException invalid(String message) {
        return new InvalidLogPipelineRuleException(message);
    }
}
