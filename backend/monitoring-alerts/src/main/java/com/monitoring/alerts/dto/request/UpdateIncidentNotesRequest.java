package com.monitoring.alerts.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Replaces the plain-text notes attached to an incident. An empty value clears them. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateIncidentNotesRequest {

    @NotNull(message = "Notes are required")
    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    @Pattern(regexp = "^[^<>]*$", message = "Notes must be plain text without HTML")
    private String notes;
}
