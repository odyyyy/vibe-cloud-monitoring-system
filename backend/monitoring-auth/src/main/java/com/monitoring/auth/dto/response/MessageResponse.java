package com.monitoring.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Generic acknowledgement used by endpoints that must not reveal whether an account
 * exists (register, resend confirmation, forgot password).
 */
@Data
@AllArgsConstructor
public class MessageResponse {
    private String message;
}
