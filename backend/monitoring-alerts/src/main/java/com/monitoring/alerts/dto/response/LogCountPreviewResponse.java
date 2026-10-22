package com.monitoring.alerts.dto.response;

import java.time.Instant;

/**
 * Result of a log alert preview: how many events matched the filters in the requested window.
 * The timestamps make the answer reproducible and let the UI label the range it measured.
 */
public record LogCountPreviewResponse(long count, int windowMinutes, Instant from, Instant to) {
}
