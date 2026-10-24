package com.monitoring.alerts.service;

import com.monitoring.alerts.dto.response.NotificationTestResult;
import com.monitoring.alerts.entity.AlertCondition;
import com.monitoring.alerts.entity.AlertRuleType;
import com.monitoring.alerts.entity.IncidentStatus;
import com.monitoring.alerts.entity.LogicOperator;
import com.monitoring.alerts.entity.NotificationChannel;
import com.monitoring.alerts.entity.NotificationChannelType;
import com.monitoring.alerts.entity.NotificationDelivery;
import com.monitoring.alerts.entity.NotificationDeliveryStatus;
import com.monitoring.alerts.event.AlertEvent;
import com.monitoring.alerts.repository.NotificationChannelRepository;
import com.monitoring.alerts.repository.NotificationDeliveryRepository;
import com.monitoring.alerts.repository.ScheduledReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for delivery persistence, retry semantics and queue backpressure. */
class NotificationDeliveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

    private NotificationDeliveryRepository deliveryRepository;
    private NotificationChannelRepository channelRepository;
    private ExecutorService executor;
    private NotificationSender sender;
    private NotificationDeliveryService service;
    private NotificationChannel channel;
    private AlertEvent event;

    @BeforeEach
    void setUp() {
        deliveryRepository = Mockito.mock(NotificationDeliveryRepository.class);
        channelRepository = Mockito.mock(NotificationChannelRepository.class);
        executor = Mockito.mock(ExecutorService.class);
        sender = Mockito.mock(NotificationSender.class);
        when(sender.type()).thenReturn(NotificationChannelType.WEBHOOK);

        service = new NotificationDeliveryService(
                deliveryRepository,
                channelRepository,
                Mockito.mock(ScheduledReportRepository.class),
                List.of(sender),
                executor,
                executor,
                3,
                1);

        channel = NotificationChannel.builder()
                .id(5L)
                .organizationId(9L)
                .type(NotificationChannelType.WEBHOOK)
                .url("https://8.8.8.8/hook")
                .active(true)
                .build();
        event = new AlertEvent(
                11L,
                NOW.minusSeconds(60),
                3L,
                "High CPU",
                9L,
                4L,
                AlertRuleType.METRIC_THRESHOLD,
                "system.cpu.usage",
                AlertCondition.GREATER_THAN,
                90.0,
                95.0,
                List.of(),
                LogicOperator.AND,
                IncidentStatus.FIRING,
                NOW,
                null);
    }

    @Test
    void retryableFailureIsRetriedAndSucceeds() {
        NotificationDelivery delivery = pendingDelivery();
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(sender.deliver(channel, event))
                .thenReturn(NotificationSendResult.httpFailure(500, "HTTP 500"))
                .thenReturn(NotificationSendResult.httpFailure(500, "HTTP 500"))
                .thenReturn(NotificationSendResult.success(200));
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.deliverWithRetries(1L, channel, event);

        verify(sender, times(3)).deliver(channel, event);
        assertEquals(NotificationDeliveryStatus.SUCCESS, delivery.getStatus());
        assertEquals(3, delivery.getAttempts());
        assertEquals(200, delivery.getLastHttpStatus());
        assertNull(delivery.getLastError());
    }

    @Test
    void permanentClientErrorIsNotRetried() {
        NotificationDelivery delivery = pendingDelivery();
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(sender.deliver(channel, event))
                .thenReturn(NotificationSendResult.httpFailure(400, "HTTP 400: bad request"));
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.deliverWithRetries(1L, channel, event);

        verify(sender, times(1)).deliver(channel, event);
        assertEquals(NotificationDeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(1, delivery.getAttempts());
    }

    @Test
    void serverErrorIsRetriedUpToTheAttemptLimit() {
        NotificationDelivery delivery = pendingDelivery();
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(sender.deliver(channel, event))
                .thenReturn(NotificationSendResult.httpFailure(503, "HTTP 503"));
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.deliverWithRetries(1L, channel, event);

        verify(sender, times(3)).deliver(channel, event);
        assertEquals(NotificationDeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(3, delivery.getAttempts());
    }

    @Test
    void synchronousTestReportsActualOutcome() {
        NotificationDelivery delivery = pendingDelivery();
        when(deliveryRepository.save(any(NotificationDelivery.class))).thenReturn(delivery);
        when(sender.deliver(channel, event)).thenReturn(NotificationSendResult.success(202));

        NotificationTestResult result = service.deliverTest(channel, event);

        assertEquals(NotificationDeliveryStatus.SUCCESS, result.status());
        assertEquals(202, result.httpStatus());
        verify(sender, times(1)).deliver(channel, event);
        assertEquals(NotificationDeliveryStatus.SUCCESS, delivery.getStatus());
    }

    @Test
    void rejectedQueueMarksDeliveryFailedWithoutBlockingTheSubmitter() {
        NotificationDelivery delivery = pendingDelivery();
        when(deliveryRepository.save(any(NotificationDelivery.class))).thenReturn(delivery);
        when(executor.submit(any(Runnable.class)))
                .thenThrow(new RejectedExecutionException("full"));

        service.enqueue(channel, event);

        verify(sender, never()).deliver(any(), any());
        assertEquals(NotificationDeliveryStatus.FAILED, delivery.getStatus());
        assertEquals("Notification delivery queue is full", delivery.getLastError());
    }

    private NotificationDelivery pendingDelivery() {
        NotificationDelivery delivery = NotificationDelivery.builder()
                .id(1L)
                .organizationId(9L)
                .channelId(5L)
                .status(NotificationDeliveryStatus.PENDING)
                .attempts(0)
                .build();
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return delivery;
    }
}
