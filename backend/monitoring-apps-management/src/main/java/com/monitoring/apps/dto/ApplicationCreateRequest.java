package com.monitoring.apps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ApplicationCreateRequest {

    @NotBlank(message = "Application name is required")
    @Size(max = 100, message = "Application name must be at most 100 characters")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
