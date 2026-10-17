package com.monitoring.storage.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes API keys with SHA-256 before they are persisted.
 *
 * <p>Only the hash is stored, so a database leak does not expose usable agent credentials.
 * Ingestion hashes the presented key and looks the hash up; the raw key is returned to the
 * user exactly once (on creation or regeneration) and is never persisted or re-read.
 *
 * <p>API keys are random 128-bit values ({@code app_} + UUID), so an unsalted SHA-256 is
 * sufficient here: there is no low-entropy secret to brute-force. Salting would also break the
 * deterministic lookup required by {@code ApiKeyAuthFilter}.
 */
public final class ApiKeyHasher {

    private static final String ALGORITHM = "SHA-256";

    private ApiKeyHasher() {
    }

    /**
     * @param rawApiKey the plaintext key presented by a client
     * @return the lowercase hex-encoded SHA-256 digest of the key
     */
    public static String hash(String rawApiKey) {
        if (rawApiKey == null) {
            throw new IllegalArgumentException("API key must not be null");
        }
        try {
            byte[] digest = MessageDigest.getInstance(ALGORITHM)
                    .digest(rawApiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ALGORITHM + " is not available", ex);
        }
    }
}
