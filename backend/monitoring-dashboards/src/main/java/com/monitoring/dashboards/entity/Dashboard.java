package com.monitoring.dashboards.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A user-owned dashboard: a named collection of widgets with a saved layout.
 *
 * <p>Ownership is expressed by {@code organizationId}; every read and write is scoped to the
 * authenticated user in the service layer (tenant invariant).
 */
@Entity
@Table(
        name = "dashboards",
        indexes = {
                @Index(name = "idx_dashboards_organization_id", columnList = "organization_id"),
                @Index(name = "idx_dashboards_share_token", columnList = "share_token", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dashboard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /**
     * Template variable definitions serialized as a JSON array. Opaque to persistence: it is
     * validated and (de)serialized in the service layer, mirroring widget config handling.
     */
    @Column(name = "variables", columnDefinition = "TEXT")
    private String variables;

    @Column(name = "share_token", length = 36, unique = true)
    private String shareToken;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "dashboard", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("positionY ASC, positionX ASC, id ASC")
    @Builder.Default
    private List<Widget> widgets = new ArrayList<>();

    /**
     * Generates a new cryptographically secure share token (UUID v4).
     */
    public void generateShareToken() {
        this.shareToken = UUID.randomUUID().toString();
        this.isPublic = true;
    }

    /**
     * Revokes the share token, making the dashboard private again.
     */
    public void revokeShareToken() {
        this.shareToken = null;
        this.isPublic = false;
    }
}
