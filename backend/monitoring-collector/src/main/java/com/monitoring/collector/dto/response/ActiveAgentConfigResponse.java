package com.monitoring.collector.dto.response;

/** Active config downloaded by an agent. The YAML is applied as an overlay on local settings. */
public record ActiveAgentConfigResponse(String configVersion, String configYaml) {
}
