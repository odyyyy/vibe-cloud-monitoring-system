package com.monitoring.alerts.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.NotificationChannelType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Request to create a notification channel.
 *
 * <p>Exactly one destination is required depending on {@link #type}: {@code url} for
 * {@code WEBHOOK}, {@code slackWebhookUrl} for {@code SLACK}, {@code emailAddress} for
 * {@code EMAIL}, {@code routingKey} for {@code PAGERDUTY} and {@code teamsWebhookUrl} for
 * {@code TEAMS}. Format/SSRF validation is enforced in the service layer (bean validation only
 * covers presence and length). A webhook may additionally carry an HMAC {@code secret} and up to
 * five static {@code headers}.
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

    @JsonAlias("webhookUrl")
    @Size(max = 2048, message = "teamsWebhookUrl must be at most 2048 characters")
    private String teamsWebhookUrl;

    @Size(max = 255, message = "routingKey must be at most 255 characters")
    private String routingKey;

    @Size(max = 255, message = "secret must be at most 255 characters")
    private String secret;

    @Size(max = 5, message = "At most 5 static headers are allowed")
    private Map<String, String> headers;

    @JsonProperty("isActive")
    private Boolean active;
}
