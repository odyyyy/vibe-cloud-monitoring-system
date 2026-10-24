package com.monitoring.alerts.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.NotificationChannelType;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Partial update of a notification channel; only non-null fields are applied.
 *
 * <p>A blank destination keeps the existing value, which lets clients update settings without
 * re-entering a URL that the API only returns in masked form. For {@code secret} and
 * {@code headers}, the presence of the field replaces the stored value (an empty value clears it).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateNotificationChannelRequest {

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

