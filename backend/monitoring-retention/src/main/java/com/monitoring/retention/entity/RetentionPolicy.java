package com.monitoring.retention.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Per-organization telemetry retention windows.
 *
 * <p>Every organization has at most one row ({@code organization_id} is unique). When no row
 * exists the configured defaults apply, so creating a policy is not required for the job to
 * run. {@code updatedBy} is the user who last changed the policy and is kept for audit
 * display, while the authoritative audit trail lives in {@code audit_events}.
 */
@Entity
@Table(
        name = "retention_policies",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_retention_policies_organization",
                columnNames = "organization_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, unique = true)
    private Long organizationId;

    @Column(name = "logs_retention_days", nullable = false)
    private int logsRetentionDays;

    @Column(name = "metrics_retention_days", nullable = false)
    private int metricsRetentionDays;

    @Column(name = "traces_retention_days", nullable = false)
    private int tracesRetentionDays;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Id of the user who last updated the policy; {@code null} for a default policy. */
    @Column(name = "updated_by")
    private Long updatedBy;
}
