package com.monitoring.alerts.entity;

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
 * A one-time window during which alert incidents and notifications are suppressed.
 *
 * <p>Ownership is expressed by {@code organizationId}; the service layer scopes every read and
 * write to the authenticated tenant. {@code scopeId} is null for a {@link
 * MaintenanceScopeType#GLOBAL} window, otherwise it references an application or an alert rule.
 *
 * <p>Times are stored and compared as UTC instants. A window only mutes while {@code active} is
 * true and the evaluation instant is inside {@code [startTime, endTime)}.
 */
@Entity
@Table(
        name = "maintenance_windows",
        indexes = {
                @Index(name = "idx_maintenance_windows_organization_id", columnList = "organization_id"),
                @Index(
                        name = "idx_maintenance_windows_org_active_time",
                        columnList = "organization_id, is_active, start_time, end_time")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceWindow {

    public static final int MAX_NAME_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "scope_type", nullable = false, length = 32)
    private MaintenanceScopeType scopeType;

    /** Application id for {@code APPLICATION}, rule id for {@code SPECIFIC_RULE}, null otherwise. */
    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
