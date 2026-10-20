package com.monitoring.auth.jwt;

import java.time.Instant;
import java.util.Set;

/**
 * Extra authentication context attached to a Personal Access Token request. It carries the
 * organization the token is bound to, the granted scopes and the id of the token itself.
 */
public record PatAuthenticationDetails(
        Long tokenId,
        Long organizationId,
        Set<String> scopes,
        Instant lastUsedAt
) {
}
