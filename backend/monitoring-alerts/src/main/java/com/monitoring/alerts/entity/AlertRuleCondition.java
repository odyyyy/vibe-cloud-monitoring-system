package com.monitoring.alerts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A single condition of an alert rule: a target ({@code target}), a comparison
 * ({@code operator}) and a {@code threshold}.
 *
 * <p>It is an embeddable value object, not an entity: conditions have no identity of their own and
 * are always loaded and replaced together with their owning {@link AlertRule}. For a
 * {@link AlertRuleType#METRIC_THRESHOLD} rule the target is a metric name; for a
 * {@link AlertRuleType#HEALTH_CHECK} rule it is a health-check id.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRuleCondition {

    public static final int MAX_TARGET_LENGTH = 255;

    @Column(name = "target", nullable = false, length = MAX_TARGET_LENGTH)
    private String target;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "operator", nullable = false, length = 16)
    private AlertCondition operator;

    @Column(name = "threshold_value", nullable = false)
    private double threshold;
}
