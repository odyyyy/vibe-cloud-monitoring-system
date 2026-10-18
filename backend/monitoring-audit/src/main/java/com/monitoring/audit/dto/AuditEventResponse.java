package com.monitoring.audit.dto;

import com.monitoring.audit.entity.AuditEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Public, read-only representation of an audit event. The persistence entity is never exposed
 * directly, and no field is writable through the API.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventResponse {

    private Long id;
    private Long organizationId;
    private Long userId;
    private String username;
    private String action;
    private String resourceId;
    private String ipAddress;
    private Instant timestamp;

    public static AuditEventResponse from(AuditEvent event, String username) {
        return AuditEventResponse.builder()
                .id(event.getId())
                .organizationId(event.getOrganizationId())
                .userId(event.getUserId())
                .username(username)
                .action(event.getAction())
                .resourceId(event.getResourceId())
                .ipAddress(event.getIpAddress())
                .timestamp(event.getTimestamp())
                .build();
    }
}
