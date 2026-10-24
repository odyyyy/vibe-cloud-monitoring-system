package com.monitoring.alerts.controller;

import com.monitoring.alerts.dto.response.NotificationDeliveryResponse;
import com.monitoring.alerts.entity.NotificationDeliveryStatus;
import com.monitoring.alerts.service.NotificationDeliveryService;
import com.monitoring.common.dto.response.PageResponse;
import com.monitoring.organization.tenant.CurrentTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only, tenant-scoped delivery log for notification channels.
 *
 * <p>The organization is always derived from the authenticated tenant; a {@code channelId} or
 * {@code incidentId} supplied by the client only narrows the result and never widens its scope.
 */
@RestController
@RequestMapping("/api/v1/notification-deliveries")
@RequiredArgsConstructor
public class NotificationDeliveryController {

    private final NotificationDeliveryService deliveryService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<PageResponse<NotificationDeliveryResponse>> list(
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) Long incidentId,
            @RequestParam(required = false) NotificationDeliveryStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(deliveryService.list(
                currentTenantService.requireOrganizationId(),
                channelId,
                incidentId,
                status,
                page,
                size));
    }
}
