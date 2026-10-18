package com.monitoring.storage.service;

import com.monitoring.storage.dto.AgentNodeResponse;
import com.monitoring.storage.entity.AgentNode;
import com.monitoring.storage.entity.Application;
import com.monitoring.storage.repository.AgentNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Tracks the agents that report telemetry for an application and exposes the fleet view.
 *
 * <p>A heartbeat is recorded on every accepted ingestion batch (see the collector's
 * {@code MetricIngestService}); it upserts one {@link AgentNode} per hostname. Listing a fleet
 * verifies application ownership first, so a user can never observe another tenant's agents.
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    // Bounded to the storage columns so an oversized tag can never fail an ingestion batch.
    private static final int MAX_HOSTNAME_LENGTH = 255;
    private static final int MAX_VERSION_LENGTH = 64;

    private final AgentNodeRepository agentNodeRepository;
    private final ApplicationService applicationService;

    /**
     * Upserts the node for {@code hostname} and refreshes its last-seen instant.
     *
     * <p>A blank hostname is ignored: without a host the batch cannot be attributed to a node.
     * The version is only overwritten when it is actually present, so an agent that omits the
     * tag does not erase a previously reported version.
     */
    @Transactional
    public void recordHeartbeat(Application application, String hostname, String agentVersion, Instant seenAt) {
        String host = truncate(hostname, MAX_HOSTNAME_LENGTH);
        if (host == null) {
            return;
        }

        AgentNode node = agentNodeRepository.findByApplicationIdAndHostname(application.getId(), host)
                .orElseGet(() -> AgentNode.builder()
                        .application(application)
                        .hostname(host)
                        .build());

        node.setLastSeenAt(seenAt);

        String version = truncate(agentVersion, MAX_VERSION_LENGTH);
        if (version != null) {
            node.setAgentVersion(version);
        }

        agentNodeRepository.save(node);
    }

    @Transactional(readOnly = true)
    public List<AgentNodeResponse> listForApplication(Long applicationId, Long organizationId) {
        applicationService.getOwnedApplication(applicationId, organizationId);
        return agentNodeRepository.findByApplicationIdOrderByHostnameAsc(applicationId)
                .stream()
                .map(AgentNodeResponse::fromEntity)
                .toList();
    }

    /** Trims the value and returns {@code null} for blanks, so empty tags are treated as absent. */
    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
