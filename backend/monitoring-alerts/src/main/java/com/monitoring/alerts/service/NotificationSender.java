package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.event.AlertEvent;

/**
 * Delivers an alert transition to a single notification channel of one type.
 *
 * <p>Implementations are invoked on a bounded executor (never on the evaluation thread) and must
 * not block indefinitely. Failures are caught by the {@code AlertNotificationDispatcher} so one
 * broken destination cannot affect the others.
 */
public interface NotificationSender {

    /** The channel type this sender handles. */
    NotificationChannelType type();

    /**
     * Delivers the event to the channel. May throw; the caller logs and isolates failures.
     */
    void deliver(NotificationChannel channel, AlertEvent event);
}
