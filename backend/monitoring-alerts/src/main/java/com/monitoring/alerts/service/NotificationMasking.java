package com.monitoring.alerts.service;

import java.net.URI;

/** Helpers that mask destinations and credentials before they leave the backend. */
public final class NotificationMasking {

    private static final String MASK = "****";

    private NotificationMasking() {
    }

    /** Masks everything except the last four characters of a secret. */
    public static String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return MASK;
        }
        return MASK + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * Masks a URL while retaining its scheme and host so users can still recognise the
     * destination. The path, query and credentials are never returned.
     */
    public static String maskUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return maskSecret(rawUrl);
            }
            String authority = uri.getPort() > 0
                    ? uri.getHost() + ":" + uri.getPort()
                    : uri.getHost();
            return uri.getScheme() + "://" + authority + "/" + MASK + lastFour(uri.getPath(), rawUrl);
        } catch (RuntimeException ex) {
            return maskSecret(rawUrl);
        }
    }

    private static String lastFour(String path, String rawUrl) {
        String source = path != null && !path.isBlank() ? path : rawUrl;
        String cleaned = source.replaceAll("/+$", "");
        if (cleaned.length() <= 4) {
            return "";
        }
        return cleaned.substring(cleaned.length() - 4);
    }
}
