package com.monitoring.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// @Getter/@Setter instead of @Data: the generated toString() would include the password.
@Getter
@Setter
public class UpdateEmailRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    @Size(max = 255, message = "Email is too long")
    private String newEmail;
}
