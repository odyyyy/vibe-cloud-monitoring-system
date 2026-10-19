package com.monitoring.alerts.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRule;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.LogicOperator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Public representation of an alert rule. The JSON field is {@code isActive} to match the
 * documented frontend contract. {@code condition}/{@code threshold}/{@code target} are the legacy
 * single-condition view and mirror the first entry of {@code conditions} for compound rules.
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
    private List<AlertConditionResponse> conditions;
    private LogicOperator logicOperator;
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
                .conditions(rule.getConditions().stream()
                        .map(AlertConditionResponse::fromEntity)
                        .toList())
                .logicOperator(rule.getLogicOperator())
                .durationMinutes(rule.getDurationMinutes())
                .active(rule.isActive())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
