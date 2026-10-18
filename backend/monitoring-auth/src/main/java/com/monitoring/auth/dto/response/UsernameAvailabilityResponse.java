package com.monitoring.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UsernameAvailabilityResponse {
    private boolean available;
}
