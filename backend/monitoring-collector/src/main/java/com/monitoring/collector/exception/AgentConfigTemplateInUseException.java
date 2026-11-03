package com.monitoring.collector.exception;

public class AgentConfigTemplateInUseException extends RuntimeException {

    public AgentConfigTemplateInUseException(Long id) {
        super("Agent config template " + id + " is assigned to an application");
    }
}
