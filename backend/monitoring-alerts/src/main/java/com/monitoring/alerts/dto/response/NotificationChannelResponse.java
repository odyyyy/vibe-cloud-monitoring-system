package com.monitoring.alerts.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Public representation of a notification channel. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationChannelResponse {

    private Long id;
    private Long organizationId;
    private NotificationChannelType type;
    private String url;

    @JsonProperty("isActive")
    private boolean active;

    public static NotificationChannelResponse fromEntity(NotificationChannel channel) {
        return NotificationChannelResponse.builder()
                .id(channel.getId())
                .organizationId(channel.getOrganizationId())
                .type(channel.getType())
                .url(channel.getUrl())
                .active(channel.isActive())
                .build();
    }
}
