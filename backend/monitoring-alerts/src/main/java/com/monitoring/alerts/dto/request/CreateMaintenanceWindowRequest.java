package com.monitoring.alerts.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monitoring.alerts.entity.MaintenanceScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Request to create a maintenance window. Scope consistency is enforced in the service layer. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMaintenanceWindowRequest {

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @NotNull(message = "scopeType is required")
    private MaintenanceScopeType scopeType;

    /** Required for APPLICATION and SPECIFIC_RULE; must be null for GLOBAL. */
    private Long scopeId;

    @NotNull(message = "startTime is required")
    private Instant startTime;

    @NotNull(message = "endTime is required")
    private Instant endTime;

    @JsonProperty("isActive")
    private Boolean active;
}
