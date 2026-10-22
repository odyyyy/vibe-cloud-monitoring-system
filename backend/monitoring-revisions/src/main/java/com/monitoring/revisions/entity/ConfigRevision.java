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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * An immutable snapshot of a versioned configuration resource after a change.
 *
 * <p>A row is never updated: it answers "what did the resource look like, and who changed it
 * to this?". The snapshot is an opaque JSON document owned by the resource module (dashboard
 * or alert rule), validated again before a restore is applied. Rows are tenant-scoped by
 * {@code organizationId} and pruned to the newest {@code N} per resource.
 */
@Entity
@Table(
        name = "config_revisions",
        indexes = @Index(
                name = "idx_config_revisions_resource",
                columnList = "organization_id, resource_type, resource_id, revision_number")
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigRevision {

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

    @Column(name = "revision_number", nullable = false, updatable = false)
    private int revisionNumber;

    /** Author of the change; null for a baseline captured from pre-feature state. */
    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "change_type", nullable = false, length = 16, updatable = false)
    private ConfigChangeType changeType;

    @Column(name = "restored_from_revision_number", updatable = false)
    private Integer restoredFromRevisionNumber;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;

    @Column(name = "snapshot", nullable = false, columnDefinition = "LONGTEXT", updatable = false)
    private String snapshot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
