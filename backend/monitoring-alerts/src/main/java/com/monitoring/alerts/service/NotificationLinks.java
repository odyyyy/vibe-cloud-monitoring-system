package com.monitoring.alerts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Builds customer-facing deep links included in notifications. */
@Component
public class NotificationLinks {

    private final String frontendBaseUrl;

    public NotificationLinks(@Value("${monitoring.alerts.frontend.base-url:}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
    }

    /**
     * Absolute incident link, or {@code null} when no frontend base URL is configured (for
     * example local development) so notifications never advertise an unreachable localhost URL.
     */
    public String incidentUrl(Long incidentId) {
        if (frontendBaseUrl.isBlank() || incidentId == null) {
            return null;
        }
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        return base + "/app/incidents?incidentId=" + incidentId;
    }
}
