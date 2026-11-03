package com.monitoring.collector.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lazily parsed JSON context for one event. The original text is returned unchanged unless a
 * pipeline action actually modified it; malformed context is preserved and context-based actions
 * are skipped rather than overwriting user data.
 */
final class LogPipelineContext {

    static final int MAX_CONTEXT_CHARS = 8192;

    private final ObjectMapper objectMapper;
    private final String original;
    private Map<String, Object> values;
    private boolean parsed;
    private boolean valid = true;
    private boolean modified;

    LogPipelineContext(ObjectMapper objectMapper, String original) {
        this.objectMapper = objectMapper;
        this.original = original;
    }

    Map<String, Object> values() {
        parse();
        return values;
    }

    boolean isValid() {
        parse();
        return valid;
    }

    boolean isModified() {
        return modified;
    }

    String stringValue(String key) {
        parse();
        if (!valid || key == null) {
            return null;
        }
        Object value = values.get(key);
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return null;
    }

    boolean put(String key, String value) {
        parse();
        if (!valid || key == null || value == null) {
            return false;
        }
        Map<String, Object> previous = new LinkedHashMap<>(values);
        values.put(key, value);
        if (!fits()) {
            values = previous;
            return false;
        }
        modified = true;
        return true;
    }

    boolean putAll(Map<String, String> additions) {
        parse();
        if (!valid || additions.isEmpty()) {
            return false;
        }
        Map<String, Object> previous = new LinkedHashMap<>(values);
        values.putAll(additions);
        if (!fits()) {
            values = previous;
            return false;
        }
        modified = true;
        return true;
    }

    boolean mask(String key, SafeRegex regex) {
        String current = stringValue(key);
        if (current == null) {
            return false;
        }
        String masked = regex.replaceAll(current);
        if (masked.equals(current)) {
            return false;
        }
        Map<String, Object> previous = new LinkedHashMap<>(values);
        values.put(key, masked);
        if (!fits()) {
            values = previous;
            return false;
        }
        modified = true;
        return true;
    }

    String serialize() {
        if (!modified || !valid) {
            return original;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return original;
        }
    }

    private boolean fits() {
        try {
            return objectMapper.writeValueAsString(values).length() <= MAX_CONTEXT_CHARS;
        } catch (Exception e) {
            return false;
        }
    }

    private void parse() {
        if (parsed) {
            return;
        }
        parsed = true;
        values = new LinkedHashMap<>();
        if (original == null || original.isBlank()) {
            return;
        }
        try {
            Map<String, Object> decoded = objectMapper.readValue(
                    original, new TypeReference<Map<String, Object>>() {
                    });
            if (decoded == null) {
                return;
            }
            values = new LinkedHashMap<>(decoded);
        } catch (Exception e) {
            valid = false;
            values = new LinkedHashMap<>();
        }
    }
}
