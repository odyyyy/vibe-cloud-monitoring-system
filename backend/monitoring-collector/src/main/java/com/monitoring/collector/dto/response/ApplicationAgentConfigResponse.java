package com.monitoring.collector.dto.response;

/**
 * Effective application configuration shown in the UI. {@code configVersion} changes whenever
 * the merged YAML changes; agents report it back after applying or rejecting it.
 */
public record ApplicationAgentConfigResponse(
        Long applicationId,
        Long assignedTemplateId,
        String assignedTemplateName,
        Long defaultTemplateId,
        String effectiveConfigYaml,
        String configVersion
) {
}
