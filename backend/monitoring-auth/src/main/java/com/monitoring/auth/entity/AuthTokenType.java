package com.monitoring.auth.entity;

/**
 * Purpose of an {@link AuthToken}. Tokens are single-use and scoped to a type so that a
 * confirmation link cannot be replayed as a password-reset link.
 */
public enum AuthTokenType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
}
