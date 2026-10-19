package com.monitoring.alerts.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.NotificationChannelType;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Partial update of a notification channel; only non-null fields are applied. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateNotificationChannelRequest {

    private NotificationChannelType type;

    @Size(min = 1, max = 2048, message = "url must be between 1 and 2048 characters")
    private String url;

    @Size(min = 1, max = 2048, message = "slackWebhookUrl must be between 1 and 2048 characters")
    private String slackWebhookUrl;

    @Size(min = 1, max = 320, message = "emailAddress must be between 1 and 320 characters")
    private String emailAddress;

    @JsonProperty("isActive")
    private Boolean active;
}
