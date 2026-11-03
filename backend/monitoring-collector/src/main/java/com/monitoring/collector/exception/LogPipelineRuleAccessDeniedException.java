package com.monitoring.collector.exception;

public class LogPipelineRuleAccessDeniedException extends RuntimeException {

    public LogPipelineRuleAccessDeniedException() {
        super("You do not have access to this log pipeline rule");
    }
}
