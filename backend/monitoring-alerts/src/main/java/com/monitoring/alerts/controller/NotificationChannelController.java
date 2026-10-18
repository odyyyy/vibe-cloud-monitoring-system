package com.monitoring.alerts.controller;

import com.monitoring.alerts.dto.request.CreateNotificationChannelRequest;
import com.monitoring.alerts.dto.response.NotificationChannelResponse;
import com.monitoring.alerts.dto.request.UpdateNotificationChannelRequest;
import com.monitoring.alerts.service.AlertService;
import com.monitoring.auth.organization.entity.MembershipRole;
import com.monitoring.auth.organization.security.RequireRole;
import com.monitoring.auth.organization.tenant.CurrentTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Notification channel CRUD for authenticated users. Webhook URLs are SSRF-validated in
 * {@link AlertService} before they are stored.
 */
@RestController
@RequestMapping("/api/v1/notification-channels")
@RequiredArgsConstructor
public class NotificationChannelController {

    private final AlertService alertService;
    private final CurrentTenantService currentTenantService;

    @GetMapping
    public ResponseEntity<List<NotificationChannelResponse>> list() {
        return ResponseEntity.ok(alertService.listChannels(currentTenantService.requireOrganizationId()));
    }

    @PostMapping
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<NotificationChannelResponse> create(
            @Valid @RequestBody CreateNotificationChannelRequest request) {
        NotificationChannelResponse created = alertService.createChannel(
                currentTenantService.requireOrganizationId(), request);
        return ResponseEntity.created(URI.create("/api/v1/notification-channels/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<NotificationChannelResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateNotificationChannelRequest request) {
        return ResponseEntity.ok(alertService.updateChannel(
                currentTenantService.requireOrganizationId(), id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole({MembershipRole.ADMIN, MembershipRole.OWNER})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        alertService.deleteChannel(currentTenantService.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }
}
