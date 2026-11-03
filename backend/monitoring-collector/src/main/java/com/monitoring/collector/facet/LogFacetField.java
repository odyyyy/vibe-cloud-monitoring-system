package com.monitoring.collector.facet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The whitelist of log fields that may be faceted.
 *
 * <p>Built-in fields carry a fixed storage location. In addition, any well-formed
 * {@code context.<key>} name may be requested; the key is validated and later used only as a
 * lookup key in the JSON document, never as SQL text.
 *
 * <ul>
 *   <li>{@code contextKey == null} — the field is the indexed {@code level} column.</li>
 *   <li>{@code contextKey != null} — the field lives inside the JSON document stored in the
 *       {@code context} column and cannot be read with an index.</li>
 * </ul>
 */
public final class LogFacetField {

    /** Prefix used to address an arbitrary top-level member of the context document. */
    public static final String CONTEXT_PREFIX = "context.";

    /** Maximum length of a dynamic context key. */
    public static final int MAX_CONTEXT_KEY_LENGTH = 64;

    private static final Pattern CONTEXT_KEY_PATTERN =
            Pattern.compile("[A-Za-z0-9_.-]{1," + MAX_CONTEXT_KEY_LENGTH + "}");

    private static final Map<String, LogFacetField> BUILT_INS;

    /** Severity: a real column ({@code log_events.level}). */
    public static final LogFacetField LEVEL = new LogFacetField("level", null, "level");

    /** Host name: a key of the JSON document in {@code log_events.context}. */
    public static final LogFacetField HOST = new LogFacetField("host", "host", "host");

    /** Service name: a key of the JSON document in {@code log_events.context}. */
    public static final LogFacetField SERVICE = new LogFacetField("service", "service", "service");

    /** Kubernetes namespace: a key of the JSON document in {@code log_events.context}. */
    public static final LogFacetField NAMESPACE =
            new LogFacetField("k8s.namespace", "k8s.namespace", "namespace");

    static {
        Map<String, LogFacetField> index = new LinkedHashMap<>();
        for (LogFacetField field : List.of(LEVEL, HOST, SERVICE, NAMESPACE)) {
            index.put(field.fieldName, field);
        }
        BUILT_INS = Map.copyOf(index);
    }

    private final String fieldName;
    private final String contextKey;
    private final String filterParam;

    private LogFacetField(String fieldName, String contextKey, String filterParam) {
        this.fieldName = fieldName;
        this.contextKey = contextKey;
        this.filterParam = filterParam;
    }

    /** The public name used in the {@code fields} query parameter. */
    public String fieldName() {
        return fieldName;
    }

    public String contextKey() {
        return contextKey;
    }

    /** The built-in query parameter for this field; {@code null} for dynamic context fields. */
    public String filterParam() {
        return filterParam;
    }

    public boolean isContextField() {
        return contextKey != null;
    }

    /** True when the field was requested as {@code context.<key>}. */
    public boolean isDynamicContextField() {
        return filterParam == null && contextKey != null;
    }

    /**
     * Resolves a public field name.
     *
     * @return the field, or empty when the name is not a built-in or a valid context field
     */
    public static Optional<LogFacetField> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        LogFacetField builtIn = BUILT_INS.get(normalized);
        if (builtIn != null) {
            return Optional.of(builtIn);
        }
        String trimmed = name.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(CONTEXT_PREFIX)) {
            return Optional.empty();
        }
        String key = trimmed.substring(CONTEXT_PREFIX.length());
        if (!isValidContextKey(key)) {
            return Optional.empty();
        }
        return Optional.of(new LogFacetField(CONTEXT_PREFIX + key, key, null));
    }

    /** Creates a dynamic field for a validated context key. */
    public static LogFacetField context(String key) {
        if (!isValidContextKey(key)) {
            throw new IllegalArgumentException("Invalid context key");
        }
        return new LogFacetField(CONTEXT_PREFIX + key, key, null);
    }

    /** A context key is lower-case/upper-case alphanumerics, dot, underscore or hyphen. */
    public static boolean isValidContextKey(String key) {
        return key != null && CONTEXT_KEY_PATTERN.matcher(key).matches();
    }

    /** The supported built-in names plus the dynamic syntax, for error messages and docs. */
    public static List<String> supportedNames() {
        return List.of("level", "host", "service", "k8s.namespace", "context.<key>");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LogFacetField field)) {
            return false;
        }
        return fieldName.equals(field.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName);
    }

    @Override
    public String toString() {
        return fieldName;
    }
}
