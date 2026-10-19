package com.monitoring.dashboards.dto.request;

import com.monitoring.dashboards.dto.DashboardVariable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDashboardRequest {

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    /**
     * Optional template variable definitions. {@code null} leaves the stored definitions
     * untouched (the rename action sends only a name); an empty list clears them.
     */
    @Size(max = 20, message = "a dashboard may define at most 20 variables")
    private List<@Valid DashboardVariable> variables;
}
