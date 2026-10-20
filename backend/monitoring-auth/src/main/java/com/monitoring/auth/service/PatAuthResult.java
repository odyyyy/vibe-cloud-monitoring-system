package com.monitoring.auth.service;

import java.time.Instant;
import java.util.Set;

/** The identity and permissions resolved from a valid Personal Access Token. */
public record PatAuthResult(
        Long tokenId,
        Long userId,
        Long organizationId,
        Set<String> scopes,
        Instant lastUsedAt
) {
}
