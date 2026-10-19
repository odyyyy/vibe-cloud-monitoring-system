package com.monitoring.auth.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Best-effort extraction of the client IP address from a request.
 *
 * <p>{@code X-Forwarded-For} is a comma-separated list that proxies append to; the first
 * entry is the originating client. The value is client-controllable unless the application
 * sits behind a trusted proxy that overwrites the header, so it is used only as a coarse
 * best-effort signal (audit trail and login throttling), never as a sole authorization input.
 *
 * <p>Lives in {@code monitoring-auth} because that is the lowest module both the audit trail
 * and the login protection can share (audit already depends on auth), so the parsing logic
 * is not duplicated.
 */
public final class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";
    private static final int MAX_LENGTH = 64;

    private ClientIpResolver() {
    }

    /** Returns a bounded client IP string, or {@code null} when nothing usable is available. */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String forwardedFor = firstUsable(request.getHeader(X_FORWARDED_FOR));
        if (forwardedFor != null) {
            return forwardedFor;
        }

        String realIp = firstUsable(request.getHeader(X_REAL_IP));
        if (realIp != null) {
            return realIp;
        }

        return truncate(request.getRemoteAddr());
    }

    private static String firstUsable(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        for (String candidate : headerValue.split(",")) {
            String value = candidate.trim();
            if (!value.isEmpty() && !UNKNOWN.equalsIgnoreCase(value)) {
                return truncate(value);
            }
        }
        return null;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }
}
