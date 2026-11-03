package com.monitoring.collector.dto.request;

import com.monitoring.collector.entity.AgentConfigTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateAgentConfigTemplateRequest {

    @NotBlank(message = "Template name is required")
    @Size(max = AgentConfigTemplate.MAX_NAME_LENGTH,
            message = "Template name must be at most 100 characters")
    private String name;

    @NotNull(message = "Config YAML is required")
    @Size(max = AgentConfigTemplate.MAX_YAML_LENGTH,
            message = "Config YAML must be at most 65536 characters")
    private String configYaml;

    private Boolean isDefault;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConfigYaml() {
        return configYaml;
    }

    public void setConfigYaml(String configYaml) {
        this.configYaml = configYaml;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }
}
