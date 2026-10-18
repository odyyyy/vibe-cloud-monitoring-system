package com.monitoring.apps.dto;

import com.monitoring.apps.entity.AgentNode;

import java.time.Instant;

/**
 * Public representation of an agent node in the application's fleet.
 *
 * <p>Carries only agent-reported metadata (hostname, optional version) and the last ingest
 * instant; the online/offline decision is derived by the client from {@code lastSeenAt} against
 * a freshness threshold. The agent's network address is never included.
 */
public record AgentNodeResponse(Long id, String hostname, String agentVersion, Instant lastSeenAt) {

    public static AgentNodeResponse fromEntity(AgentNode node) {
        return new AgentNodeResponse(node.getId(), node.getHostname(), node.getAgentVersion(), node.getLastSeenAt());
    }
}
