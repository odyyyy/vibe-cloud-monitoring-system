package com.monitoring.alerts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A single firing episode of an alert rule.
 *
 * <p>{@code organizationId} is denormalized so incidents can be listed and scoped to a tenant
 * without joining the rule. Incidents are removed when their rule is deleted (FK cascade).
 */
@Entity
@Table(
        name = "alert_incidents",
        indexes = {
                @Index(name = "idx_alert_incidents_rule_id", columnList = "rule_id"),
                @Index(name = "idx_alert_incidents_organization_id", columnList = "organization_id"),
                @Index(name = "idx_alert_incidents_triggered_at", columnList = "triggered_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertIncident {

    public static final int MAX_NOTES_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private AlertRule rule;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private IncidentStatus status;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    /** User who first acknowledged the incident; {@code null} while it is only firing. */
    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /** User who closed the incident, or {@code null} when it was resolved automatically. */
    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Plain-text team notes; arbitrary HTML is rejected by the service. */
    @Column(length = 1000)
    private String notes;
}
