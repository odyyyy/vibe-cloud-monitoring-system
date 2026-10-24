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
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Audit-friendly record of one notification delivery attempt series to one channel.
 *
 * <p>The row is created as {@link NotificationDeliveryStatus#PENDING} before the asynchronous
 * send starts, updated after every attempt, and finishes as {@code SUCCESS} or {@code FAILED}.
 * {@code lastError} is truncated and never contains secrets from the channel configuration.
 * Rows are tenant-scoped by {@code organizationId} and survive channel deletion so operators can
 * still understand historical failures.
 */
@Entity
@Table(
        name = "notification_deliveries",
        indexes = {
                @Index(name = "idx_notification_deliveries_organization_id",
                        columnList = "organization_id"),
                @Index(name = "idx_notification_deliveries_org_created",
                        columnList = "organization_id,created_at"),
                @Index(name = "idx_notification_deliveries_channel_id", columnList = "channel_id"),
                @Index(name = "idx_notification_deliveries_incident_id", columnList = "incident_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDelivery {

    public static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    /** Channel that received the delivery; null for an email digest sent to explicit recipients. */
    @Column(name = "channel_id", updatable = false)
    private Long channelId;

    /** Incident that caused the delivery; null for test and scheduled-report deliveries. */
    @Column(name = "incident_id", updatable = false)
    private Long incidentId;

    /** Scheduled report that produced the delivery; null for alert and test deliveries. */
    @Column(name = "scheduled_report_id", updatable = false)
    private Long scheduledReportId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 32, updatable = false)
    private NotificationEventType eventType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private NotificationDeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    public boolean isTest() {
        return incidentId == null && scheduledReportId == null;
    }
}
