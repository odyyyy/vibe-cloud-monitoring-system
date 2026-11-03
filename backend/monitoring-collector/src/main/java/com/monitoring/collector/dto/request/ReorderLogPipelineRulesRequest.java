package com.monitoring.collector.dto.request;

import com.monitoring.collector.entity.LogPipelineRule;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** New order for every rule owned by the organization, first to last. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderLogPipelineRulesRequest {

    @NotEmpty(message = "ids is required")
    @Size(max = LogPipelineRule.MAX_RULES_PER_ORGANIZATION,
            message = "At most " + LogPipelineRule.MAX_RULES_PER_ORGANIZATION + " rules can be reordered")
    private List<@NotNull Long> ids;
}
