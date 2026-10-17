package com.monitoring.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Response DTO: @Getter only, so the access token never appears in a generated toString().
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;
}