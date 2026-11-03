package com.monitoring.collector.dto.request;

import jakarta.validation.constraints.Positive;

/** {@code null} removes the application-specific template and falls back to the org default. */
public class AssignAgentConfigTemplateRequest {

    @Positive(message = "Template id must be positive")
    private Long templateId;

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }
}
