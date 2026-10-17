package com.monitoring.alerts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A user-owned alert rule.
 *
 * <p>Ownership is expressed by {@code ownerId}; every read and write is scoped to the
 * authenticated user in the service layer (tenant invariant). {@code target} is either a
 * metric name ({@link AlertRuleType#METRIC_THRESHOLD}) or a health-check id
 * ({@link AlertRuleType#HEALTH_CHECK}).
 *
 * <p>{@code breachStartedAt} tracks how long the rule has been continuously breaching so a
 * short spike does not fire an incident. It is internal evaluation state and is not part of
 * the public API.
 */
@Entity
@Table(
        name = "alert_rules",
        indexes = {
                @Index(name = "idx_alert_rules_owner_id", columnList = "owner_id"),
                @Index(name = "idx_alert_rules_is_active", columnList = "is_active")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_TARGET_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** Required for {@link AlertRuleType#METRIC_THRESHOLD}; null for health-check rules. */
    @Column(name = "application_id")
    private Long applicationId;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    // Store enum names as VARCHAR to stay portable across databases (Hibernate on MySQL
    // would otherwise expect a native ENUM column).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private AlertRuleType type;

    @Column(nullable = false, length = MAX_TARGET_LENGTH)
    private String target;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "condition_type", nullable = false, length = 16)
    private AlertCondition condition;

    @Column(name = "threshold_value", nullable = false)
    private double threshold;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "breach_started_at")
    private Instant breachStartedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
