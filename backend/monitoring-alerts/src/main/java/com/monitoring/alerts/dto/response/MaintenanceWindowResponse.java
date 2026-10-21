package com.monitoring.alerts.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.MaintenanceScopeType;
import com.monitoring.alerts.entity.MaintenanceWindow;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Public representation of a maintenance window. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceWindowResponse {

    private Long id;
    private Long organizationId;
    private String name;
    private MaintenanceScopeType scopeType;
    private Long scopeId;
    private Instant startTime;
    private Instant endTime;

    @JsonProperty("isActive")
    private boolean active;

    private Instant createdAt;
    private Instant updatedAt;

    public static MaintenanceWindowResponse fromEntity(MaintenanceWindow window) {
        return MaintenanceWindowResponse.builder()
                .id(window.getId())
                .organizationId(window.getOrganizationId())
                .name(window.getName())
                .scopeType(window.getScopeType())
                .scopeId(window.getScopeId())
                .startTime(window.getStartTime())
                .endTime(window.getEndTime())
                .active(window.isActive())
                .createdAt(window.getCreatedAt())
                .updatedAt(window.getUpdatedAt())
                .build();
    }
}
