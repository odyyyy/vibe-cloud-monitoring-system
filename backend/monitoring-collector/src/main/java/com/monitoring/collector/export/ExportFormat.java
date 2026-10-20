package com.monitoring.collector.export;

import com.monitoring.collector.exception.InvalidExportFormatException;

import java.util.Locale;

/**
 * Download format requested by the {@code format} query parameter.
 *
 * <p>When the parameter is absent the query endpoints keep their regular JSON response
 * shape. An explicit {@code format=json} returns a downloadable JSON array instead.
 */
public enum ExportFormat {

    CSV,
    JSON;

    /**
     * Parses the optional {@code format} query parameter. Values are case-insensitive so both
     * {@code csv} and {@code CSV} work.
     *
     * @return {@code null} when the parameter is absent, so callers can keep backward
     *         compatibility with the existing (paginated) response contract
     */
    public static ExportFormat parseOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "csv" -> CSV;
            case "json" -> JSON;
            default -> throw new InvalidExportFormatException();
        };
    }
}
