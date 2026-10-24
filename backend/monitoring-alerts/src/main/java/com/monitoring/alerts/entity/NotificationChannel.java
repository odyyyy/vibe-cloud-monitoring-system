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
 * {@link NotificationChannelType#SLACK}, {@code emailAddress} for
 * {@link NotificationChannelType#EMAIL}, {@code pagerDutyRoutingKey} for
 * {@link NotificationChannelType#PAGERDUTY} and {@code teamsWebhookUrl} for
 * {@link NotificationChannelType#TEAMS}. Webhook, Slack and Teams URLs are validated for SSRF
 * safety (https only, public host) before they are stored and again right before each delivery.
 * Ownership is enforced by {@code organizationId} in the service layer.
 *
 * <p>Like the existing Slack webhook URLs, the webhook secret, static headers and PagerDuty
 * routing key are stored in plaintext in the application database. They are never returned by the
 * API (responses expose masked values) or written to logs/audit entries.
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
    public static final int MAX_SECRET_LENGTH = 255;
    public static final int MAX_ROUTING_KEY_LENGTH = 255;
    public static final int MAX_HEADERS = 5;
    public static final int MAX_HEADER_NAME_LENGTH = 128;
    public static final int MAX_HEADER_VALUE_LENGTH = 1024;
    public static final int MAX_HEADERS_JSON_LENGTH = 4096;

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

    /** Destination for a {@link NotificationChannelType#TEAMS} channel; null otherwise. */
    @Column(name = "teams_webhook_url", length = MAX_URL_LENGTH)
    private String teamsWebhookUrl;

    /** PagerDuty Events API v2 integration key; null for other channel types. */
    @Column(name = "pagerduty_routing_key", length = MAX_ROUTING_KEY_LENGTH)
    private String pagerDutyRoutingKey;

    /** Optional HMAC-SHA256 secret for generic webhooks; null when signing is disabled. */
    @Column(name = "webhook_secret", length = MAX_SECRET_LENGTH)
    private String webhookSecret;

    /** Optional static webhook headers serialized as a JSON object (maximum five). */
    @Column(name = "webhook_headers", length = MAX_HEADERS_JSON_LENGTH)
    private String webhookHeaders;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
