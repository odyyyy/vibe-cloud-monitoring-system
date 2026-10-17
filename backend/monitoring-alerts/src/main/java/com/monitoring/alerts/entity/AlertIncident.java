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
 * <p>{@code ownerId} is denormalized so incidents can be listed and scoped to a tenant
 * without joining the rule. Incidents are removed when their rule is deleted (FK cascade).
 */
@Entity
@Table(
        name = "alert_incidents",
        indexes = {
                @Index(name = "idx_alert_incidents_rule_id", columnList = "rule_id"),
                @Index(name = "idx_alert_incidents_owner_id", columnList = "owner_id"),
                @Index(name = "idx_alert_incidents_triggered_at", columnList = "triggered_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private AlertRule rule;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private IncidentStatus status;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
