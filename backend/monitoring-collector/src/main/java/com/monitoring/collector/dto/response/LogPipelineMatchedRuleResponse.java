package com.monitoring.collector.dto.response;

import com.monitoring.collector.entity.LogPipelineAction;

/** A rule that matched during a pipeline test, in evaluation order. */
public record LogPipelineMatchedRuleResponse(Long id, String name, LogPipelineAction action) {
}
