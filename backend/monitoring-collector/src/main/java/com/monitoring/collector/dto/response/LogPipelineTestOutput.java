package com.monitoring.collector.dto.response;

import com.monitoring.collector.entity.LogLevel;

/** Final event produced by a pipeline test, or null when the event was dropped. */
public record LogPipelineTestOutput(LogLevel level, String message, String context) {
}
