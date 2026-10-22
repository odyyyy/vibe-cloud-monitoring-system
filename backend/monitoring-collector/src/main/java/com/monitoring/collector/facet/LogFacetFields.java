package com.monitoring.collector.facet;

import com.monitoring.collector.exception.InvalidLogFacetRequestException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses and validates the {@code fields} parameter of the facet endpoint.
 *
 * <p>The parameter is a comma-separated list of whitelisted field names. Unknown names are
 * rejected with HTTP 400; the name is only ever used as a lookup key, never as part of SQL.
 */
public final class LogFacetFields {

    private LogFacetFields() {
    }

    /**
     * Parses the requested fields.
     *
     * @param fields the raw comma-separated parameter, may be {@code null}
     * @return the requested fields in the order they were requested
     * @throws InvalidLogFacetRequestException when a name is unknown, the list is empty or the
     *         list is longer than the allowed maximum
     */
    public static List<LogFacetField> parse(String fields, int maxFields) {
        if (fields == null || fields.isBlank()) {
            throw new InvalidLogFacetRequestException(
                    "fields is required; supported fields: " + String.join(", ", LogFacetField.supportedNames()));
        }

        Set<LogFacetField> requested = new LinkedHashSet<>();
        for (String rawName : fields.split(",")) {
            String name = rawName.trim();
            if (name.isEmpty()) {
                continue;
            }
            requested.add(LogFacetField.fromName(name).orElseThrow(() -> new InvalidLogFacetRequestException(
                    "Unknown facet field '" + name + "'; supported fields: "
                            + String.join(", ", LogFacetField.supportedNames()))));
        }

        if (requested.isEmpty()) {
            throw new InvalidLogFacetRequestException(
                    "fields is required; supported fields: " + String.join(", ", LogFacetField.supportedNames()));
        }
        if (requested.size() > maxFields) {
            throw new InvalidLogFacetRequestException(
                    "At most " + maxFields + " facet fields can be requested at once");
        }
        return new ArrayList<>(requested);
    }
}
