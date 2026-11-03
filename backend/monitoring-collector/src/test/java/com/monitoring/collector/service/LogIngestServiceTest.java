package com.monitoring.collector.service;

import com.monitoring.apps.entity.Application;
import com.monitoring.collector.dto.request.LogEventPayload;
import com.monitoring.collector.dto.request.LogIngestRequest;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogLevel;
import com.monitoring.collector.pipeline.LogPipelineEngine;
import com.monitoring.collector.repository.LogEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the fallback pipeline boundary: dropped events must never reach persistence and the
 * stored event must contain the transformed message/context produced by the engine.
 */
@ExtendWith(MockitoExtension.class)
class LogIngestServiceTest {

    @Mock
    private LogEventRepository logEventRepository;

    @Mock
    private LogPipelineService logPipelineService;

    @InjectMocks
    private LogIngestService logIngestService;

    @Test
    void persistsOnlyEventsThatSurviveThePipeline() {
        Application application = Application.builder().id(7L).build();
        Instant timestamp = Instant.parse("2026-10-29T00:00:00Z");
        List<LogEventPayload> payloads = List.of(
                payload(LogLevel.DEBUG, "cache miss", "{}", timestamp),
                payload(LogLevel.ERROR, "order 42 failed", "{\"service\":\"checkout\"}", timestamp));
        when(logPipelineService.applyForIngestBatch(eq(application), anyList()))
                .thenReturn(List.of(
                        new LogPipelineEngine.PipelineResult(true, List.of(), null, null),
                        new LogPipelineEngine.PipelineResult(
                                false, List.of(), "order [REDACTED] failed",
                                "{\"order_id\":\"42\"}")));

        int accepted = logIngestService.ingest(application, request(payloads));

        assertEquals(1, accepted);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LogEvent>> saved = ArgumentCaptor.forClass(List.class);
        verify(logEventRepository).saveAll(saved.capture());
        List<LogEvent> events = saved.getValue();
        assertEquals(1, events.size());
        LogEvent event = events.get(0);
        assertEquals(LogLevel.ERROR, event.getLevel());
        assertEquals("order [REDACTED] failed", event.getMessage());
        assertEquals("{\"order_id\":\"42\"}", event.getContext());
        assertEquals(timestamp, event.getTimestamp());
        assertNotNull(event.getReceivedAt());
    }

    @Test
    void doesNotWriteWhenEveryEventIsDropped() {
        Application application = Application.builder().id(7L).build();
        LogEventPayload payload =
                payload(LogLevel.DEBUG, "cache miss", "{}", Instant.parse("2026-10-29T00:00:00Z"));
        when(logPipelineService.applyForIngestBatch(eq(application), anyList()))
                .thenReturn(List.of(
                        new LogPipelineEngine.PipelineResult(true, List.of(), null, null)));

        int accepted = logIngestService.ingest(application, request(List.of(payload)));

        assertEquals(0, accepted);
        verify(logEventRepository, never()).saveAll(any());
    }

    private static LogEventPayload payload(LogLevel level, String message, String context,
                                           Instant timestamp) {
        return LogEventPayload.builder()
                .level(level)
                .message(message)
                .context(context)
                .timestamp(timestamp)
                .build();
    }

    private static LogIngestRequest request(List<LogEventPayload> events) {
        return LogIngestRequest.builder().events(events).build();
    }
}
