package com.monitoring.collector.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.monitoring.collector.entity.LogPipelineAction;
import com.monitoring.collector.entity.LogPipelineRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Creates a log pipeline rule. The filter and action configuration are validated in the service. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLogPipelineRuleRequest {

    @NotBlank(message = "name is required")
    @Size(max = LogPipelineRule.MAX_NAME_LENGTH,
            message = "name must be at most " + LogPipelineRule.MAX_NAME_LENGTH + " characters")
    private String name;

    /** Null means the rule applies to every application in the organization. */
    private Long applicationId;

    @NotBlank(message = "filter is required")
    @Size(max = LogPipelineRule.MAX_FILTER_LENGTH,
            message = "filter must be at most " + LogPipelineRule.MAX_FILTER_LENGTH + " characters")
    private String filter;

    @NotNull(message = "action is required")
    private LogPipelineAction action;

    private JsonNode actionConfig;
}
