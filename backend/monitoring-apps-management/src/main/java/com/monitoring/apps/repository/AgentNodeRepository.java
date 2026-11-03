package com.monitoring.apps.repository;

import com.monitoring.apps.entity.AgentNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentNodeRepository extends JpaRepository<AgentNode, Long> {

    /** Looks up the node for a single host so heartbeats update instead of inserting duplicates. */
    Optional<AgentNode> findByApplicationIdAndHostname(Long applicationId, String hostname);

    /** Fleet listing is scoped to one application and ordered deterministically by hostname. */
    List<AgentNode> findByApplicationIdOrderByHostnameAsc(Long applicationId);

    /** Marks every known agent of an application as awaiting the new config version. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AgentNode n
            SET n.configStatus = 'PENDING', n.configError = NULL
            WHERE n.application.id = :applicationId
            """)
    int markConfigPendingByApplicationId(@Param("applicationId") Long applicationId);

    /** Marks every known agent in a tenant as awaiting a changed organization default. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AgentNode n
            SET n.configStatus = 'PENDING', n.configError = NULL
            WHERE n.application.organizationId = :organizationId
            """)
    int markConfigPendingByOrganizationId(@Param("organizationId") Long organizationId);
}
