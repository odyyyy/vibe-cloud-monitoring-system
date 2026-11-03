package com.monitoring.collector.exception;

public class AgentConfigTemplateNotFoundException extends RuntimeException {

    public AgentConfigTemplateNotFoundException(Long id) {
        super("Agent config template " + id + " was not found");
    }
}
