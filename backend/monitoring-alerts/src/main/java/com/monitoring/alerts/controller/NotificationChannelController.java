package com.monitoring.alerts.controller;

import com.monitoring.alerts.dto.CreateNotificationChannelRequest;
import com.monitoring.alerts.dto.NotificationChannelResponse;
import com.monitoring.alerts.dto.UpdateNotificationChannelRequest;
import com.monitoring.alerts.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @GetMapping
    public ResponseEntity<List<NotificationChannelResponse>> list() {
        return ResponseEntity.ok(alertService.listChannels(currentUserId()));
    }

    @PostMapping
    public ResponseEntity<NotificationChannelResponse> create(
            @Valid @RequestBody CreateNotificationChannelRequest request) {
        NotificationChannelResponse created = alertService.createChannel(currentUserId(), request);
        return ResponseEntity.created(URI.create("/api/v1/notification-channels/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationChannelResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateNotificationChannelRequest request) {
        return ResponseEntity.ok(alertService.updateChannel(currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        alertService.deleteChannel(currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;

        if (principal instanceof String userId) {
            try {
                return Long.parseLong(userId);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid user ID in authentication token");
            }
        }
        throw new IllegalStateException("No authenticated user available");
    }
}
