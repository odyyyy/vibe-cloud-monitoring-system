package com.monitoring.collector.facet;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The whitelist of log fields that may be faceted.
 *
 * <p>The public name is the only thing a client may send. It is used exclusively as a lookup
 * key into {@link #fromName(String)}; it is never concatenated into SQL. Every enum constant
 * carries the storage location of the field:
 *
 * <ul>
 *   <li>{@code contextKey == null} — the field is a real, indexed column of {@code log_events}
 *       ({@code level}) and is aggregated by the database.</li>
 *   <li>{@code contextKey != null} — the field lives inside the JSON document stored in the
 *       {@code context} column ({@code host}, {@code service}, {@code k8s.namespace}) and cannot
 *       be read with an index.</li>
 * </ul>
 */
public enum LogFacetField {

    /** Severity: a real column ({@code log_events.level}). */
    LEVEL("level", null, "level"),

    /** Host name: a key of the JSON document in {@code log_events.context}. */
    HOST("host", "host", "host"),

    /** Service name: a key of the JSON document in {@code log_events.context}. */
    SERVICE("service", "service", "service"),

    /** Kubernetes namespace: a key of the JSON document in {@code log_events.context}. */
    NAMESPACE("k8s.namespace", "k8s.namespace", "namespace");

    private static final Map<String, LogFacetField> BY_NAME = buildIndex();

    private final String fieldName;
    private final String contextKey;
    private final String filterParam;

    LogFacetField(String fieldName, String contextKey, String filterParam) {
        this.fieldName = fieldName;
        this.contextKey = contextKey;
        this.filterParam = filterParam;
    }

    /** The public name used in the {@code fields} query parameter. */
    public String fieldName() {
        return fieldName;
    }

    /**
     * The key inside the JSON {@code context} document, or {@code null} when the field is a real
     * column of {@code log_events}.
     */
    public String contextKey() {
        return contextKey;
    }

    /** The query parameter that carries the exact-match filter for this field. */
    public String filterParam() {
        return filterParam;
    }

    /** True when the value is stored in the JSON {@code context} document. */
    public boolean isContextField() {
        return contextKey != null;
    }

    /**
     * Resolves a public field name.
     *
     * @return the field, or {@link Optional#empty()} when the name is not whitelisted
     */
    public static Optional<LogFacetField> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    /** The whitelisted field names, in a stable order, for error messages and documentation. */
    public static List<String> supportedNames() {
        return Arrays.stream(values()).map(LogFacetField::fieldName).toList();
    }

    private static Map<String, LogFacetField> buildIndex() {
        Map<String, LogFacetField> index = new LinkedHashMap<>();
        for (LogFacetField field : values()) {
            index.put(field.fieldName, field);
        }
        return Map.copyOf(index);
    }
}
