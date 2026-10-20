package com.monitoring.auth.dto.pat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PatCreateRequest(
        @NotBlank(message = "Name must not be empty")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotNull(message = "Expiration is required")
        @Min(value = 1, message = "Expiration must be at least 1 day")
        @Max(value = 365, message = "Expiration must be at most 365 days")
        Integer expiresDays,

        @NotEmpty(message = "Select at least one scope")
        @Size(max = 32, message = "Too many scopes")
        List<@NotBlank(message = "Scope must not be blank") String> scopes
) {
}
