package com.monitoring.auth.entity;

/**
 * Platform-wide role of a human account.
 *
 * <p>Organization roles ({@code OWNER}/{@code ADMIN}/{@code VIEWER}) are a separate concept:
 * they are scoped to one organization and never grant access to platform internals. Only
 * {@link #PLATFORM_ADMIN} may see the platform self-monitoring dashboard and its telemetry.
 */
public enum Role {
    USER,
    PLATFORM_ADMIN
}
