package com.monitoring.apps.dto.response;

import java.time.Instant;

/**
 * Response returned when an API key is issued.
 *
 * <p>This is the only place the plaintext {@code apiKey} is ever exposed. It is shown to the
 * owner once (on creation or regeneration) and is not retrievable afterwards, because only its
 * hash is persisted. {@code apiKeyHint} is a short non-sensitive mask that stays available in
 * subsequent list/get responses so the owner can identify the key.
 */
public record ApplicationWithKeyResponse(
        Long id, String name, String apiKey, String apiKeyHint, Instant createdAt) {
}
