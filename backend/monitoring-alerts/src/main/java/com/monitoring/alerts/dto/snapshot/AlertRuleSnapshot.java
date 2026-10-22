package com.monitoring.alerts.dto.snapshot;

import com.monitoring.alerts.dto.request.AlertConditionRequest;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.collector.entity.LogLevel;

import java.util.List;

/**
 * JSON document stored in an alert rule revision. Contains only the user-editable definition;
 * tenant ownership, ids, incident state and notification channels are deliberately excluded.
 */
public record AlertRuleSnapshot(
        String name,
        AlertRuleType type,
        Long applicationId,
        Integer durationMinutes,
        LogicOperator logicOperator,
        Boolean active,
        List<AlertConditionRequest> conditions,
        LogLevel logLevel,
        String logQuery,
        Integer windowMinutes,
        AlertCondition comparison,
        Long countThreshold
) {
}
