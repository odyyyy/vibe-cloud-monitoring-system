package com.monitoring.api.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resource types supported by the global search endpoint.
 *
 * <p>The wire names are part of the frontend contract and must not change silently. New types
 * must also be added to the frontend resource route mapping.
 */
public enum SearchResourceType {
    APPLICATIONS("applications"),
    DASHBOARDS("dashboards"),
    ALERT_RULES("alertRules"),
    HEALTH_CHECKS("healthChecks"),
    SAVED_VIEWS("savedViews"),
    INCIDENTS("incidents");

    private static final Map<String, SearchResourceType> BY_WIRE_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    type -> type.wireName.toLowerCase(Locale.ROOT),
                    Function.identity()));

    private final String wireName;

    SearchResourceType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    /** Case-insensitive lookup used for the optional {@code types} query parameter. */
    public static Optional<SearchResourceType> fromWireName(String value) {
        return Optional.ofNullable(BY_WIRE_NAME.get(value.trim().toLowerCase(Locale.ROOT)));
    }
}
