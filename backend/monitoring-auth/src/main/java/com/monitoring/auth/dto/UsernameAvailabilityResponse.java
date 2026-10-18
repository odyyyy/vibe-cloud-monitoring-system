package com.monitoring.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UsernameAvailabilityResponse {
    private boolean available;
}
