package com.monitoring.alerts.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.MaintenanceScopeType;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Partial update of a maintenance window. Setting {@code isActive} to false ends a window early;
 * omitted fields keep their current value.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMaintenanceWindowRequest {

    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    private MaintenanceScopeType scopeType;

    private Long scopeId;

    private Instant startTime;

    private Instant endTime;

    @JsonProperty("isActive")
    private Boolean active;
}
