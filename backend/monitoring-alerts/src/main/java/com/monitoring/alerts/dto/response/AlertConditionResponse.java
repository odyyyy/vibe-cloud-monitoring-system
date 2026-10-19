package com.monitoring.alerts.dto.response;

import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Public representation of a single condition inside a compound alert rule. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertConditionResponse {

    private String target;
    private AlertCondition operator;
    private double threshold;

    public static AlertConditionResponse fromEntity(AlertRuleCondition condition) {
        return AlertConditionResponse.builder()
                .target(condition.getTarget())
                .operator(condition.getOperator())
                .threshold(condition.getThreshold())
                .build();
    }
}
