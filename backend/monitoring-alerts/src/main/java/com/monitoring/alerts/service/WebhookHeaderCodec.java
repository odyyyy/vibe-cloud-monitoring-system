package com.monitoring.alerts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.exception.InvalidNotificationChannelException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates, serializes and parses the static headers of a generic webhook channel.
 *
 * <p>Header names follow the HTTP token grammar. Hop-by-hop and platform-owned headers are
 * rejected so a tenant cannot override the request framing or the platform's signature headers.
 */
public final class WebhookHeaderCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> TYPE =
            new TypeReference<>() {
            };
    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final Set<String> RESERVED = Set.of(
            "host",
            "content-length",
            "connection",
            "transfer-encoding",
            "upgrade",
            "x-signature-256",
            "x-timestamp");

    private WebhookHeaderCodec() {
    }

    /**
     * Validates and serializes the headers, returning {@code null} for an empty map.
     *
     * @throws InvalidNotificationChannelException when the count, names or values are invalid
     */
    public static String serialize(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        if (headers.size() > NotificationChannel.MAX_HEADERS) {
            throw new InvalidNotificationChannelException(
                    "At most " + NotificationChannel.MAX_HEADERS + " static headers are allowed");
        }

        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            validateName(name);
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new InvalidNotificationChannelException(
                        "Header '" + name + "' is configured more than once");
            }
            if (value.length() > NotificationChannel.MAX_HEADER_VALUE_LENGTH) {
                throw new InvalidNotificationChannelException(
                        "Header values must be at most "
                                + NotificationChannel.MAX_HEADER_VALUE_LENGTH + " characters");
            }
            if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new InvalidNotificationChannelException("Header values must not contain line breaks");
            }
            normalized.put(name, value);
        }

        try {
            return MAPPER.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            throw new InvalidNotificationChannelException("Static headers could not be stored");
        }
    }

    /** Parses stored headers; malformed legacy data is treated as no headers. */
    public static LinkedHashMap<String, String> parse(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            LinkedHashMap<String, String> parsed = MAPPER.readValue(json, TYPE);
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    private static void validateName(String name) {
        if (name.isEmpty() || name.length() > NotificationChannel.MAX_HEADER_NAME_LENGTH) {
            throw new InvalidNotificationChannelException(
                    "Header names must be between 1 and "
                            + NotificationChannel.MAX_HEADER_NAME_LENGTH + " characters");
        }
        if (!HEADER_NAME.matcher(name).matches()) {
            throw new InvalidNotificationChannelException("Header name '" + name + "' is not valid");
        }
        if (RESERVED.contains(name.toLowerCase(Locale.ROOT))) {
            throw new InvalidNotificationChannelException(
                    "Header '" + name + "' is managed by the platform and cannot be set");
        }
    }
}
