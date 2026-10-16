package com.monitoring.healthcheck.entity;

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
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A user-owned synthetic HTTP check configuration.
 *
 * <p>Ownership is expressed by {@code ownerId}; every read and write is scoped to the
 * authenticated user in the service layer (tenant invariant). The {@code targetUrl} is
 * validated for SSRF safety before it is stored and again right before execution.
 *
 * <p>{@code lastCheckedAt} is internal scheduling state and is not part of the public API.
 */
@Entity
@Table(
        name = "health_checks",
        indexes = @Index(name = "idx_health_checks_owner_id", columnList = "owner_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthCheckConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private HealthCheckMethod method;

    @Column(name = "expected_status_code", nullable = false)
    private int expectedStatusCode;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;
}
