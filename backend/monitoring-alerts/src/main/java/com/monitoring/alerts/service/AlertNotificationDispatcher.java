package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Fans an alert transition out to every active notification channel of the owning tenant.
 *
 * <p>Delivery is asynchronous and bounded: each send is submitted to a dedicated executor, so a
 * slow or hostile endpoint can never block evaluation or exhaust threads. Failures are isolated to
 * the affected channel. A per-rule/per-channel rate limiter drops repeats inside the cooldown
 * window to prevent notification flooding.
 */
@Component
@Slf4j
public class AlertNotificationDispatcher {

    private final NotificationChannelRepository channelRepository;
    private final Map<NotificationChannelType, NotificationSender> senders;
    private final NotificationRateLimiter rateLimiter;
    private final ExecutorService webhookExecutor;
    private final ExecutorService emailExecutor;

    public AlertNotificationDispatcher(
            NotificationChannelRepository channelRepository,
            List<NotificationSender> notificationSenders,
            NotificationRateLimiter rateLimiter,
            @Qualifier("alertWebhookExecutor") ExecutorService alertWebhookExecutor,
            @Qualifier("alertEmailExecutor") ExecutorService alertEmailExecutor) {
        this.channelRepository = channelRepository;
        this.rateLimiter = rateLimiter;
        this.webhookExecutor = alertWebhookExecutor;
        this.emailExecutor = alertEmailExecutor;
        Map<NotificationChannelType, NotificationSender> byType = new EnumMap<>(NotificationChannelType.class);
        for (NotificationSender sender : notificationSenders) {
            byType.put(sender.type(), sender);
        }
        this.senders = byType;
    }

    /** Queues a notification for every active channel of the event's owner. */
    public void dispatch(AlertEvent event) {
        List<NotificationChannel> channels =
                channelRepository.findByOrganizationIdAndActiveTrue(event.organizationId());
        for (NotificationChannel channel : channels) {
            submit(channel, event, false);
        }
    }

    /**
     * Delivers a single ad-hoc (test) notification to one channel, bypassing the rate limiter so a
     * user can always verify their configuration.
     */
    public void dispatchTest(NotificationChannel channel, AlertEvent event) {
        submit(channel, event, true);
    }

    private void submit(NotificationChannel channel, AlertEvent event, boolean bypassRateLimit) {
        NotificationSender sender = senders.get(channel.getType());
        if (sender == null) {
            log.warn("No notification sender for channel type {}", channel.getType());
            return;
        }

        String key = event.ruleId() + ":" + channel.getId();
        if (!bypassRateLimit && !rateLimiter.tryAcquire(key, Instant.now())) {
            log.info("Notification rate limit hit for rule {} channel {}", event.ruleId(), channel.getId());
            return;
        }

        Runnable task = () -> {
            try {
                sender.deliver(channel, event);
            } catch (Exception ex) {
                // A dead endpoint, SMTP failure, or blocked URL must not affect other deliveries.
                log.warn("Notification delivery to channel {} failed: {}", channel.getId(), ex.getMessage());
            }
        };
        executorFor(channel.getType()).submit(task);
    }

    private ExecutorService executorFor(NotificationChannelType type) {
        return type == NotificationChannelType.EMAIL ? emailExecutor : webhookExecutor;
    }
}
