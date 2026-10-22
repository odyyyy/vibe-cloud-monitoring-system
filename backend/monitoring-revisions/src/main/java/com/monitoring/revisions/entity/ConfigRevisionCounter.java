package com.monitoring.revisions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Allocates the revision numbers of one configuration resource.
 *
 * <p>The counter is incremented with a single atomic {@code UPDATE}, so two concurrent saves
 * of the same dashboard or alert rule cannot allocate the same revision number. The row lock of
 * the counter also serializes the writers of a resource, which keeps the retained history
 * consistent.
 */
@Entity
@Table(
        name = "config_revision_counters",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_config_revision_counter",
                columnNames = {"organization_id", "resource_type", "resource_id"}),
        indexes = @Index(
                name = "idx_config_revision_counters_resource",
                columnList = "organization_id, resource_type, resource_id")
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigRevisionCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "resource_type", nullable = false, length = 32, updatable = false)
    private ConfigResourceType resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private Long resourceId;

    /** The last revision number handed out for the resource. */
    @Column(name = "last_number", nullable = false)
    private int lastNumber;
}
