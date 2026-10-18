package com.monitoring.apps.dto.response;

import java.time.Instant;

/**
 * Response returned when an API key is issued.
 *
 * <p>This is the only place the plaintext {@code apiKey} is ever exposed. It is shown to the
 * owner once (on creation or regeneration) and is not retrievable afterwards, because only its
 * hash is persisted.
 */
public record ApplicationWithKeyResponse(Long id, String name, String apiKey, Instant createdAt) {
}
