package com.monitoring.alerts.service;

import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies fan-out, maintenance suppression and per-transition rate limiting. */
class AlertNotificationDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");
    private static final Long ORGANIZATION_ID = 9L;
    private static final Long RULE_ID = 3L;
    private static final Long CHANNEL_ID = 5L;

    private NotificationChannelRepository channelRepository;
    private NotificationDeliveryService deliveryService;
    private MaintenanceWindowService maintenanceWindowService;
    private NotificationChannel channel;
    private AlertNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        channelRepository = Mockito.mock(NotificationChannelRepository.class);
        deliveryService = Mockito.mock(NotificationDeliveryService.class);
        maintenanceWindowService = Mockito.mock(MaintenanceWindowService.class);
        dispatcher = new AlertNotificationDispatcher(
                channelRepository,
                deliveryService,
                new NotificationRateLimiter(900),
                maintenanceWindowService);

        channel = NotificationChannel.builder()
                .id(CHANNEL_ID)
                .organizationId(ORGANIZATION_ID)
                .type(NotificationChannelType.WEBHOOK)
                .url("https://8.8.8.8/hook")
                .active(true)
                .build();
        when(channelRepository.findByOrganizationIdAndActiveTrue(ORGANIZATION_ID))
                .thenReturn(List.of(channel));
        when(maintenanceWindowService.isMuted(
                Mockito.eq(ORGANIZATION_ID), Mockito.isNull(), Mockito.eq(RULE_ID), any(Instant.class)))
                .thenReturn(false);
    }

    @Test
    void openAcknowledgeAndResolveAreAllDeliveredWithinTheCooldownWindow() {
        dispatcher.dispatch(event(IncidentStatus.FIRING));
        dispatcher.dispatch(event(IncidentStatus.ACKNOWLEDGED));
        dispatcher.dispatch(event(IncidentStatus.RESOLVED));

        verify(deliveryService, times(3)).enqueue(any(), any());
        verify(deliveryService).enqueue(channel, event(IncidentStatus.FIRING));
        verify(deliveryService).enqueue(channel, event(IncidentStatus.ACKNOWLEDGED));
        verify(deliveryService).enqueue(channel, event(IncidentStatus.RESOLVED));
    }

    @Test
    void repeatedTransitionInsideTheCooldownIsSuppressed() {
        dispatcher.dispatch(event(IncidentStatus.FIRING));
        dispatcher.dispatch(event(IncidentStatus.FIRING));

        verify(deliveryService, times(1)).enqueue(any(), any());
    }

    @Test
    void maintenanceWindowSuppressesAllDeliveries() {
        when(maintenanceWindowService.isMuted(
                Mockito.eq(ORGANIZATION_ID), Mockito.isNull(), Mockito.eq(RULE_ID), any(Instant.class)))
                .thenReturn(true);

        dispatcher.dispatch(event(IncidentStatus.FIRING));

        verify(deliveryService, never()).enqueue(any(), any());
    }

    private AlertEvent event(IncidentStatus status) {
        return new AlertEvent(
                11L,
                NOW.minusSeconds(60),
                RULE_ID,
                "High CPU",
                ORGANIZATION_ID,
                null,
                AlertRuleType.METRIC_THRESHOLD,
                "system.cpu.usage",
                AlertCondition.GREATER_THAN,
                90.0,
                95.0,
                List.of(),
                LogicOperator.AND,
                status,
                NOW,
                null);
    }
}
