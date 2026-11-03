package com.monitoring.apps.dto.response;

import com.monitoring.apps.entity.AgentNode;

import java.time.Instant;

/**
 * Public representation of an agent node in the application's fleet.
 *
 * <p>Carries agent-reported metadata (hostname, optional version), last ingest instant and the
 * latest centralized-config sync state. The online/offline decision is derived by the client
 * from {@code lastSeenAt}; the agent's network address is never included.
 */
public record AgentNodeResponse(
        Long id,
        String hostname,
        String agentVersion,
        Instant lastSeenAt,
        String configStatus,
        String configVersion,
        String configError,
        Instant configReportedAt
) {

    public static AgentNodeResponse fromEntity(AgentNode node) {
        return new AgentNodeResponse(
                node.getId(),
                node.getHostname(),
                node.getAgentVersion(),
                node.getLastSeenAt(),
                node.getConfigStatus() == null ? "UNKNOWN" : node.getConfigStatus(),
                node.getConfigVersion(),
                node.getConfigError(),
                node.getConfigReportedAt());
    }
}
