package com.monitoring.collector.exception;

public class AgentConfigTemplateLimitExceededException extends RuntimeException {

    public AgentConfigTemplateLimitExceededException(int limit) {
        super("An organization can have at most " + limit + " agent config templates");
    }
}
