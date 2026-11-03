package com.monitoring.collector.dto.response;

import java.util.List;

/** Rules the agent must apply, sorted by evaluation order. */
public record AgentLogPipelineRulesResponse(List<LogPipelineRuleResponse> rules) {
}
