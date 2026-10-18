package com.monitoring.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An immutable record of a security-relevant action performed by a user.
 *
 * <p>Audit events are append-only: every column is {@code updatable = false}, there is no
 * setter, and no service or endpoint exposes a way to modify or delete a row. The tenant
 * scope is the {@code organizationId}; reads are filtered by it so members only see events of
 * the organization they are acting in. {@code userId} identifies the acting user.
 *
 * <p>{@code timestamp} is the server-side instant (UTC) at which the action completed.
 * {@code ipAddress} is best-effort client information derived from the request headers.
 */
@Entity
@Table(
        name = "audit_events",
        indexes = {
                @Index(name = "idx_audit_events_organization_id", columnList = "organization_id"),
                @Index(name = "idx_audit_events_organization_action", columnList = "organization_id,action"),
                @Index(name = "idx_audit_events_user_id", columnList = "user_id"),
                @Index(name = "idx_audit_events_event_time", columnList = "event_time")
        }
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Machine-readable action code, for example {@code API_KEY_REGENERATED}. */
    @Column(nullable = false, length = 64, updatable = false)
    private String action;

    /** Identifier of the affected resource; may be {@code null} when not applicable. */
    @Column(name = "resource_id", length = 128, updatable = false)
    private String resourceId;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "event_time", nullable = false, updatable = false)
    private Instant timestamp;
}
