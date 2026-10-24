package com.monitoring.alerts.service;

import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.apps.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves the application name included in notification payloads.
 *
 * <p>Log alerts already carry the name in their event details. Metric and health-check events do
 * not, so the name is loaded from the tenant-scoped application service. A missing application
 * only removes the display name; it never blocks the notification.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertApplicationNameResolver {

    private final ApplicationService applicationService;

    public String resolve(AlertEvent event) {
        if (event.logDetails() != null && event.logDetails().applicationName() != null) {
            return event.logDetails().applicationName();
        }
        if (event.applicationId() == null || event.organizationId() == null) {
            return null;
        }
        try {
            return applicationService
                    .getOwnedApplication(event.applicationId(), event.organizationId())
                    .getName();
        } catch (RuntimeException ex) {
            log.debug("Application {} for alert {} is unavailable ({})",
                    event.applicationId(), event.ruleId(), ex.getClass().getSimpleName());
            return null;
        }
    }
}
