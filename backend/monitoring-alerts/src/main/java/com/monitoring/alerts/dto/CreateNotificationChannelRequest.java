package com.monitoring.alerts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.NotificationChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request to create a notification channel. SSRF/https validation of {@code url} is enforced in
 * the service layer (bean validation only covers presence and length).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateNotificationChannelRequest {

    @NotNull(message = "type is required")
    private NotificationChannelType type;

    @NotBlank(message = "url is required")
    @Size(max = 2048, message = "url must be at most 2048 characters")
    private String url;

    @JsonProperty("isActive")
    private Boolean active;
}
