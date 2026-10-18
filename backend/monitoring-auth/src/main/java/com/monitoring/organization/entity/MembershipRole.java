package com.monitoring.organization.entity;

/**
 * Role of a user inside a single organization.
 *
 * <p>The roles are deliberately small and flat: {@link #OWNER} administers the organization
 * itself (members and deletion), {@link #ADMIN} manages telemetry resources, and
 * {@link #VIEWER} is read-only.
 */
public enum MembershipRole {
    OWNER,
    ADMIN,
    VIEWER
}
