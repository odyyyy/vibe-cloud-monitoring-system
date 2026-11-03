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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A user-owned synthetic check configuration.
 *
 * <p>Ownership is expressed by {@code organizationId}; every read and write is scoped to the
 * authenticated user in the service layer (tenant invariant). For {@link HealthCheckType#SINGLE_HTTP}
 * checks {@code targetUrl} carries the endpoint; for {@link HealthCheckType#MULTI_STEP_API} checks
 * the workflow lives in {@code stepsJson} and {@code targetUrl} is {@code null}. Every URL is
 * validated for SSRF safety before it is stored and again right before execution.
 *
 * <p>{@code lastCheckedAt} is internal scheduling state and is not part of the public API.
 */
@Entity
@Table(
        name = "health_checks",
        indexes = @Index(name = "idx_health_checks_organization_id", columnList = "organization_id")
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

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 100)
    private String name;

    // Store enum names as VARCHAR to stay portable across databases (Hibernate on MySQL
    // would otherwise expect a native ENUM column).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "check_type", nullable = false, length = 32)
    private HealthCheckType type;

    @Column(name = "target_url", length = 2048)
    private String targetUrl;

    /**
     * Serialized {@link com.monitoring.healthcheck.dto.SyntheticApiStep} array for
     * {@link HealthCheckType#MULTI_STEP_API} checks. Stored as TEXT (JSON) rather than a native
     * JSON column to stay portable across MySQL and H2.
     */
    @Column(name = "steps_json", columnDefinition = "TEXT")
    private String stepsJson;

    // Store enum names as VARCHAR to stay portable across databases (Hibernate on MySQL
    // would otherwise expect a native ENUM column).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
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
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;
}
