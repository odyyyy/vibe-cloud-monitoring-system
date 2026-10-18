package com.monitoring.organization.exception;

/**
 * The caller is authenticated but acts outside an organization they belong to, or does not
 * hold the role required for the operation. Mapped to HTTP 403.
 */
public class TenantAccessDeniedException extends RuntimeException {

    public TenantAccessDeniedException() {
        super("You do not have access to this organization");
    }

    public TenantAccessDeniedException(String message) {
        super(message);
    }
}
