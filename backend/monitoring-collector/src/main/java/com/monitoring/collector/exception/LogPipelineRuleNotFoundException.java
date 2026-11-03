package com.monitoring.collector.exception;

public class LogPipelineRuleNotFoundException extends RuntimeException {

    public LogPipelineRuleNotFoundException(Long id) {
        super("Log pipeline rule " + id + " was not found");
    }
}
