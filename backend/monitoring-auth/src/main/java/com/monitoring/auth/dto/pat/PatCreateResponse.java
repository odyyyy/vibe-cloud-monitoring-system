package com.monitoring.auth.dto.pat;

import java.time.Instant;
import java.util.Set;

public record PatCreateResponse(
        String rawToken,
        Long tokenId,
        String name,
        Instant expiresAt,
        Set<String> scopes
) {
}
