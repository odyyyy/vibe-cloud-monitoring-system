package com.monitoring.statuspage.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class CreateStatusPageRequest {

    @NotBlank(message = "slug is required")
    @Pattern(
            regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
            message = "slug may only contain lowercase letters, digits and single hyphens")
    @Size(min = 3, max = 50, message = "slug must be between 3 and 50 characters")
    private String slug;

    @NotBlank(message = "title is required")
    @Size(max = 150, message = "title must be at most 150 characters")
    private String title;

    @Size(max = 500, message = "description must be at most 500 characters")
    private String description;

    @JsonProperty("isActive")
    private Boolean active;

    @Valid
    @Size(max = 20, message = "a status page may have at most 20 components")
    private List<StatusPageComponentRequest> components;
}
