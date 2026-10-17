package com.monitoring.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// @Getter/@Setter instead of @Data: Lombok's generated toString() on @Data would include the
// token and the new password, which could leak into logs.
@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String newPassword;
}
