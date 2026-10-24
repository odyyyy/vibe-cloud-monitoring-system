package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.response.NotificationTestResult;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Fans an alert transition out to every active notification channel of the owning tenant.
 *
 * <p>Delivery itself is asynchronous and bounded (see {@code NotificationDeliveryService}), so a
 * slow or hostile endpoint can never block evaluation. A per-rule/per-channel rate limiter drops
 * repeats inside the cooldown window to prevent notification flooding. Maintenance windows
 * suppress manual transitions as well as automatic evaluation events.
 */
@Component
@Slf4j
public class AlertNotificationDispatcher {

    private final NotificationChannelRepository channelRepository;
    private final NotificationDeliveryService deliveryService;
    private final NotificationRateLimiter rateLimiter;
    private final MaintenanceWindowService maintenanceWindowService;

    public AlertNotificationDispatcher(
            NotificationChannelRepository channelRepository,
            NotificationDeliveryService deliveryService,
            NotificationRateLimiter rateLimiter,
            MaintenanceWindowService maintenanceWindowService) {
        this.channelRepository = channelRepository;
        this.deliveryService = deliveryService;
        this.rateLimiter = rateLimiter;
        this.maintenanceWindowService = maintenanceWindowService;
    }

    /** Queues a notification for every active channel of the event's owner. */
    public void dispatch(AlertEvent event) {
        if (maintenanceWindowService.isMuted(
                event.organizationId(), event.applicationId(), event.ruleId(), event.timestamp())) {
            log.info("Notification for rule {} is muted by a maintenance window", event.ruleId());
            return;
        }

        List<NotificationChannel> channels =
                channelRepository.findByOrganizationIdAndActiveTrue(event.organizationId());
        for (NotificationChannel channel : channels) {
            String key = event.ruleId() + ":" + channel.getId() + ":" + event.status().name();
            if (!rateLimiter.tryAcquire(key, Instant.now())) {
                log.info("Notification rate limit hit for rule {} channel {}", event.ruleId(), channel.getId());
                continue;
            }
            deliveryService.enqueue(channel, event);
        }
    }

    /**
     * Delivers a single ad-hoc (test) notification to one channel, bypassing both the rate limiter
     * and maintenance windows so a user can always verify their configuration.
     */
    public NotificationTestResult dispatchTest(NotificationChannel channel, AlertEvent event) {
        return deliveryService.deliverTest(channel, event);
    }
}
