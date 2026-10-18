package com.monitoring.collector.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A user-saved set of log-explorer filters (a "saved view").
 *
 * <p>Scoped to its owner: every read and write is filtered by {@code organizationId}, which is the
 * tenant boundary in the current model. The query string is stored as a canonical JSON document
 * and is always re-validated on write; it is treated as data, never as an executable expression.
 */
@Entity
@Table(
        name = "saved_log_views",
        indexes = @Index(name = "idx_saved_log_views_organization_id", columnList = "organization_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedLogView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Canonical JSON of a validated
     * {@link com.monitoring.collector.dto.request.SavedLogViewQuery}. Stored as text and never
     * concatenated into queries or interpreted as an instruction.
     */
    @Column(name = "query_params", nullable = false, columnDefinition = "TEXT")
    private String queryParams;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
