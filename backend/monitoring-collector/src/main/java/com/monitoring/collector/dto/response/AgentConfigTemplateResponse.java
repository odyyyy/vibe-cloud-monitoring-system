package com.monitoring.collector.dto.response;

import com.monitoring.collector.entity.AgentConfigTemplate;

import java.time.Instant;

public record AgentConfigTemplateResponse(
        Long id,
        String name,
        String configYaml,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {

    public static AgentConfigTemplateResponse fromEntity(AgentConfigTemplate template) {
        return new AgentConfigTemplateResponse(
                template.getId(),
                template.getName(),
                template.getConfigYaml(),
                template.isDefault(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
