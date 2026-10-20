package com.monitoring.auth.dto.pat;

import java.time.Instant;
import java.util.List;

public record PatSummary(
        Long id,
        String name,
        Instant expiresAt,
        Instant lastUsedAt,
        List<String> scopes
) {
}