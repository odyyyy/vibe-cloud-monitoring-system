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
 * A user-owned destination for alert notifications.
 *
 * <p>The single destination field depends on {@link #type}: {@code url} for
 * {@link NotificationChannelType#WEBHOOK}, {@code slackWebhookUrl} for
 * {@link NotificationChannelType#SLACK} and {@code emailAddress} for
 * {@link NotificationChannelType#EMAIL}. Webhook and Slack URLs are validated for SSRF safety
 * (https only, public host) before they are stored and again right before each delivery
 * ({@code SsrfGuard}). Ownership is enforced by {@code organizationId} in the service layer.
 */
@Entity
@Table(
        name = "notification_channels",
        indexes = @Index(name = "idx_notification_channels_organization_id", columnList = "organization_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationChannel {

    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_EMAIL_LENGTH = 320;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private NotificationChannelType type;

    /** Destination for a {@link NotificationChannelType#WEBHOOK} channel; null otherwise. */
    @Column(length = MAX_URL_LENGTH)
    private String url;

    /** Destination for a {@link NotificationChannelType#SLACK} channel; null otherwise. */
    @Column(name = "slack_webhook_url", length = MAX_URL_LENGTH)
    private String slackWebhookUrl;

    /** Destination for a {@link NotificationChannelType#EMAIL} channel; null otherwise. */
    @Column(name = "email_address", length = MAX_EMAIL_LENGTH)
    private String emailAddress;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
