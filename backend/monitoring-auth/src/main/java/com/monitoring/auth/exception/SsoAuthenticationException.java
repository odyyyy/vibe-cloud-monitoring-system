package com.monitoring.auth.exception;

/**
 * Raised when an identity returned by the OIDC provider cannot be provisioned into a local
 * account (for example, the provider did not supply an email address). It is translated into an
 * authentication failure by the SSO user service, never into a server error.
 */
public class SsoAuthenticationException extends RuntimeException {

    public SsoAuthenticationException(String message) {
        super(message);
    }
}
