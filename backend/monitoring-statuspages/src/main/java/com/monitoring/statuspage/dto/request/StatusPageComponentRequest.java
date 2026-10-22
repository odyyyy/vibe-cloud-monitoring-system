package com.monitoring.statuspage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusPageComponentRequest {

    @NotBlank(message = "component name is required")
    @Size(max = 80, message = "component name must be at most 80 characters")
    private String name;

    private Long linkedHealthCheckId;
}
