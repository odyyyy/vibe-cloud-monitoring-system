package com.monitoring.collector.exception;

public class AgentConfigTemplateNameConflictException extends RuntimeException {

    public AgentConfigTemplateNameConflictException(String name) {
        super("An agent config template named \"" + name + "\" already exists");
    }
}
