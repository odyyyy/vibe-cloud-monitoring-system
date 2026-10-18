package com.monitoring.apps.repository;

import com.monitoring.apps.entity.AgentNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentNodeRepository extends JpaRepository<AgentNode, Long> {

    /** Looks up the node for a single host so heartbeats update instead of inserting duplicates. */
    Optional<AgentNode> findByApplicationIdAndHostname(Long applicationId, String hostname);

    /** Fleet listing is scoped to one application and ordered deterministically by hostname. */
    List<AgentNode> findByApplicationIdOrderByHostnameAsc(Long applicationId);
}
