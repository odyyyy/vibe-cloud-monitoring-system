package com.monitoring.collector.facet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

/**
 * Canonical JSON encoding of a {@code log_events.context} member.
 *
 * <p>The context column stores a JSON document produced by the agent (Go's
 * {@code encoding/json}). The facet filter has to match that document textually, because the
 * JSON path functions of MySQL are not available on every database the platform runs on (H2 in
 * the test and default development setup has none), and because {@code context} is a plain
 * {@code TEXT} column that may hold values which are not JSON at all.
 *
 * <p>This class therefore encodes a member exactly the way Go's {@code encoding/json} does:
 * {@code "} and {@code \} are escaped, control characters are written as {@code \n}, {@code \r},
 * {@code \t} or a {@code \\u00xx} escape, and {@code <}, {@code >} and {@code &} are written as
 * {@code \\u003c}, {@code \\u003e} and {@code \\u0026}. The resulting text is used as a literal
 * needle for a {@code LOCATE(context)} predicate, so it is never interpreted as SQL.
 */
public final class LogContextValue {

    /** Line separator: escaped by Go's encoder, kept literal by most other encoders. */
    private static final char LINE_SEPARATOR = 0x2028;

    /** Paragraph separator: escaped by Go's encoder, kept literal by most other encoders. */
    private static final char PARAGRAPH_SEPARATOR = 0x2029;

    private LogContextValue() {
    }

    /**
     * Encodes a context member, for example {@code "host":"server-1"}. The result contains the
     * key, the colon and the quoted value, exactly as they appear in the stored document.
     */
    public static String member(String key, String value) {
        return quote(key) + ":" + quote(value);
    }

    /** Encodes a single JSON string, including the surrounding quotes. */
    public static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c");
                case '>' -> out.append("\\u003e");
                case '&' -> out.append("\\u0026");
                default -> {
                    if (c < 0x20 || c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    /**
     * Reads a single top-level value from a context document.
     *
     * @param reader a reader for the JSON tree, reused across rows
     * @param context the stored context text; may be {@code null} or not be JSON at all
     * @param key     the key to read
     * @return the decoded value, or {@code null} when the document is missing, invalid, has no
     *         such key, or the value is not a scalar
     */
    public static String extract(ObjectReader reader, String context, String key) {
        if (context == null || context.isBlank()) {
            return null;
        }
        try {
            JsonNode root = reader.readTree(context);
            if (root == null || !root.isObject()) {
                return null;
            }
            JsonNode node = root.get(key);
            if (node == null || node.isNull() || !node.isValueNode()) {
                return null;
            }
            return node.asText();
        } catch (JsonProcessingException e) {
            // The context column accepts arbitrary text, so a malformed document is expected
            // data, not an error: the row simply has no value for this field.
            return null;
        }
    }

    /** Convenience overload for callers that own an {@link ObjectMapper}. */
    public static String extract(ObjectMapper objectMapper, String context, String key) {
        return extract(objectMapper.reader(), context, key);
    }
}
