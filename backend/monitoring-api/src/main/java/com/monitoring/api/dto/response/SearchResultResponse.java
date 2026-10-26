package com.monitoring.api.dto.response;

/**
 * One resource match returned by the global command palette.
 *
 * <p>Only non-sensitive display fields are exposed; credentials, URLs and telemetry payloads
 * are never part of a search result.
 */
public record SearchResultResponse(
        String type,
        Long id,
        String title,
        String subtitle
) {
}
