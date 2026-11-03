package com.monitoring.collector.dto.request;

import com.monitoring.collector.entity.AgentConfigStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AgentConfigStatusRequest {

    @NotBlank(message = "Hostname is required")
    @Size(max = 255, message = "Hostname must be at most 255 characters")
    private String hostname;

    @NotBlank(message = "Config version is required")
    @Size(max = 64, message = "Config version must be at most 64 characters")
    private String configVersion;

    @NotNull(message = "Config status is required")
    private AgentConfigStatus status;

    @Size(max = 1024, message = "Config error must be at most 1024 characters")
    private String errorMessage;

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    public AgentConfigStatus getStatus() {
        return status;
    }

    public void setStatus(AgentConfigStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
