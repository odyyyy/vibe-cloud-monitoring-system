package com.monitoring.alerts.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.service.NotificationMasking;
import com.monitoring.alerts.service.WebhookHeaderCodec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Public representation of a notification channel.
 *
 * <p>URLs are masked (scheme and host remain visible, path/query do not) and secrets, routing
 * keys and static header values are never returned. {@code hasWebhookSecret} and
 * {@code webhookHeaderNames} let the UI show what is configured without disclosing values.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationChannelResponse {

    private Long id;
    private Long organizationId;
    private NotificationChannelType type;
    private String url;
    private String slackWebhookUrl;
    private String emailAddress;
    private String teamsWebhookUrl;
    private String pagerDutyRoutingKey;
    private boolean hasWebhookSecret;
    private List<String> webhookHeaderNames;

    @JsonProperty("isActive")
    private boolean active;

    public static NotificationChannelResponse fromEntity(NotificationChannel channel) {
        return NotificationChannelResponse.builder()
                .id(channel.getId())
                .organizationId(channel.getOrganizationId())
                .type(channel.getType())
                .url(NotificationMasking.maskUrl(channel.getUrl()))
                .slackWebhookUrl(NotificationMasking.maskUrl(channel.getSlackWebhookUrl()))
                .emailAddress(channel.getEmailAddress())
                .teamsWebhookUrl(NotificationMasking.maskUrl(channel.getTeamsWebhookUrl()))
                .pagerDutyRoutingKey(NotificationMasking.maskSecret(channel.getPagerDutyRoutingKey()))
                .hasWebhookSecret(channel.getWebhookSecret() != null && !channel.getWebhookSecret().isBlank())
                .webhookHeaderNames(List.copyOf(
                        WebhookHeaderCodec.parse(channel.getWebhookHeaders()).keySet()))
                .active(channel.isActive())
                .build();
    }
}
