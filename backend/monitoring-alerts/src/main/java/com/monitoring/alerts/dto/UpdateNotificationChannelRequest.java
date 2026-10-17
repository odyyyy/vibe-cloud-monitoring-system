package com.monitoring.alerts.dto;

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

    @JsonProperty("isActive")
    private Boolean active;
}
