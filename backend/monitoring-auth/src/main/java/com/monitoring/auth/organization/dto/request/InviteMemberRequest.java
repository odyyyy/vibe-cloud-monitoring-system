package com.monitoring.auth.organization.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteMemberRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "A valid email is required")
        @Size(max = 255, message = "Email is too long")
        String email) {
}
