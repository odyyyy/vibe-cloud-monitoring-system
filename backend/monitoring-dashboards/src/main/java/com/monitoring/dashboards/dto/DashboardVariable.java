package com.monitoring.dashboards.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * A dashboard-level template variable definition (Grafana-style). Only the definition is
 * persisted; the value selected in the UI lives in client-side state.
 *
 * <p>{@code name} is restricted to letters, digits and underscores so that {@code ${name}}
 * interpolation stays a plain string replacement and can never introduce executable content.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardVariable {

    @NotBlank(message = "variable name is required")
    @Size(max = 64, message = "variable name must be at most 64 characters")
    @Pattern(regexp = "[A-Za-z0-9_]+",
            message = "variable name may contain only letters, digits and underscores")
    private String name;

    @NotBlank(message = "variable type is required")
    @Pattern(regexp = "custom|query", message = "variable type must be 'custom' or 'query'")
    private String type;

    @NotNull(message = "variable options are required")
    @Size(min = 1, max = 50, message = "variable must have between 1 and 50 options")
    private List<@NotBlank(message = "option must not be blank")
            @Size(max = 200, message = "option must be at most 200 characters") String> options;

    @NotNull(message = "variable default value is required")
    @Size(max = 200, message = "variable default value must be at most 200 characters")
    @Builder.Default
    private String defaultValue = "";
}
