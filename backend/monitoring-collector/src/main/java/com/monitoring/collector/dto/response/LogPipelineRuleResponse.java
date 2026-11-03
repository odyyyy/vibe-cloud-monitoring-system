package com.monitoring.collector.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.monitoring.collector.entity.LogPipelineAction;
import com.monitoring.collector.entity.LogPipelineRule;

import java.time.Instant;
import java.util.List;

/** Public representation of a log pipeline rule and the fields its regex extracts. */
public record LogPipelineRuleResponse(
        Long id,
        Long organizationId,
        Long applicationId,
        String name,
        String filter,
        LogPipelineAction action,
        JsonNode actionConfig,
        int orderIndex,
        List<String> extractedFields,
        Instant createdAt,
        Instant updatedAt) {

    public static LogPipelineRuleResponse fromEntity(LogPipelineRule rule,
                                                     JsonNode actionConfig,
                                                     List<String> extractedFields) {
        return new LogPipelineRuleResponse(
                rule.getId(),
                rule.getOrganizationId(),
                rule.getApplication() == null ? null : rule.getApplication().getId(),
                rule.getName(),
                rule.getFilterExpression(),
                rule.getAction(),
                actionConfig,
                rule.getOrderIndex(),
                extractedFields == null ? List.of() : extractedFields,
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }
}
