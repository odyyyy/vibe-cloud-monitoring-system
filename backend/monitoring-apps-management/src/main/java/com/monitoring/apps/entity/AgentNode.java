package com.monitoring.apps.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A single installed agent instance, identified per application by its hostname.
 *
 * <p>Nodes are not enrolled explicitly: a row is created or refreshed whenever the application
 * ingests telemetry carrying a {@code host} tag. {@code lastSeenAt} is the ingest instant, from
 * which the fleet view derives the online/offline state. Only non-sensitive metadata provided by
 * the agent (hostname and version) is stored; the agent's IP address is never recorded.
 *
 * <p>Tenant isolation is inherited from {@link Application}, which is verified before a node is
 * returned to a user (see {@code AgentService}).
 */
@Entity
@Table(
        name = "agent_nodes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_agent_nodes_application_hostname",
                columnNames = {"application_id", "hostname"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false, length = 255)
    private String hostname;

    /** Agent-reported version; optional because it depends on the telemetry contract. */
    @Column(name = "agent_version", length = 64)
    private String agentVersion;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
