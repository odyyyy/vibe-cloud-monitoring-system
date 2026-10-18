package com.monitoring.alerts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Public representation of an alert rule. The JSON field is {@code isActive} to match the
 * documented frontend contract.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRuleResponse {

    private Long id;
    private Long organizationId;
    private Long applicationId;
    private String name;
    private AlertRuleType type;
    private String target;
    private AlertCondition condition;
    private double threshold;
    private int durationMinutes;

    @JsonProperty("isActive")
    private boolean active;

    private Instant createdAt;
    private Instant updatedAt;

    public static AlertRuleResponse fromEntity(AlertRule rule) {
        return AlertRuleResponse.builder()
                .id(rule.getId())
                .organizationId(rule.getOrganizationId())
                .applicationId(rule.getApplicationId())
                .name(rule.getName())
                .type(rule.getType())
                .target(rule.getTarget())
                .condition(rule.getCondition())
                .threshold(rule.getThreshold())
                .durationMinutes(rule.getDurationMinutes())
                .active(rule.isActive())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
