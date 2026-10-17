package com.monitoring.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// @Getter/@Setter instead of @Data: the generated toString() on @Data would include the token.
@Getter
@Setter
public class VerifyEmailRequest {

    @NotBlank(message = "Token is required")
    private String token;
}
