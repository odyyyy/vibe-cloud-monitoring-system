package com.monitoring.collector.dto.response;

import java.util.List;

/** Result of applying the persisted rules to a sample log event. */
public record LogPipelineTestResponse(
        boolean dropped,
        List<LogPipelineMatchedRuleResponse> matchedRules,
        LogPipelineTestOutput output) {
}
