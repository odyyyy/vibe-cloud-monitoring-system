package com.monitoring.alerts.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.NotificationChannelType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request to create a notification channel.
 *
 * <p>Exactly one destination is required depending on {@link #type}: {@code url} for
 * {@code WEBHOOK}, {@code slackWebhookUrl} for {@code SLACK} and {@code emailAddress} for
 * {@code EMAIL}. Format/SSRF validation is enforced in the service layer (bean validation only
 * covers presence and length).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateNotificationChannelRequest {

    @NotNull(message = "type is required")
    private NotificationChannelType type;

    @Size(max = 2048, message = "url must be at most 2048 characters")
    private String url;

    @Size(max = 2048, message = "slackWebhookUrl must be at most 2048 characters")
    private String slackWebhookUrl;

    @Size(max = 320, message = "emailAddress must be at most 320 characters")
    private String emailAddress;

    @JsonProperty("isActive")
    private Boolean active;
}
