package com.monitoring.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// @Getter/@Setter instead of @Data: Lombok's generated toString() on @Data would include the
// password, which could leak into logs.
@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
