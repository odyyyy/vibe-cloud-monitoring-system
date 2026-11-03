package com.monitoring.collector.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * A tenant-owned YAML template for dynamically managed agent settings.
 *
 * <p>At most one template per organization is the default. An application can be assigned a
 * template that is deeply merged over the organization default by the active-config endpoint.
 * The YAML is intentionally stored as authored: the agent is the authority that validates the
 * runtime schema, and rejects an invalid document while keeping its last known good config.
 */
@Entity
@Table(
        name = "agent_config_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_agent_config_templates_org_name",
                columnNames = {"organization_id", "name"}
        ),
        indexes = @Index(
                name = "idx_agent_config_templates_org_default",
                columnList = "organization_id, is_default"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentConfigTemplate {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_YAML_LENGTH = 64 * 1024;
    public static final int MAX_TEMPLATES_PER_ORGANIZATION = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "config_yaml", nullable = false, columnDefinition = "TEXT")
    private String configYaml;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
