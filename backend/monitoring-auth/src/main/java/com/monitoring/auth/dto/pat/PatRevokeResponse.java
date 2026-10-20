package com.monitoring.auth.dto.pat;

public record PatRevokeResponse(
        Long tokenId,
        String message
) {
}