package com.monitoring.collector.exception;

public class LogPipelineRuleLimitExceededException extends RuntimeException {

    public LogPipelineRuleLimitExceededException(int limit) {
        super("At most " + limit + " log pipeline rules are allowed per organization");
    }
}
