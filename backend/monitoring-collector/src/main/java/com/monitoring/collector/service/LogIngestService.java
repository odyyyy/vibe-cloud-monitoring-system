package com.monitoring.collector.service;

import com.monitoring.collector.dto.request.LogEventPayload;
import com.monitoring.collector.dto.request.LogIngestRequest;
import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.pipeline.LogPipelineEngine;
import com.monitoring.collector.repository.LogEventRepository;
import com.monitoring.apps.entity.Application;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Accepts log batches from agents and persists them scoped to the owning application.
 *
 * <p>The payload is validated at the API boundary ({@code @Valid}): an empty batch, more
 * than {@link LogIngestRequest#MAX_BATCH_SIZE} events or oversized fields are rejected with
 * HTTP 400. Persistence is synchronous, so HTTP 202 means the batch was actually stored.
 *
 * <p>Log contents are never logged by this service: they are untrusted and may contain
 * secrets.
 */
@Service
@RequiredArgsConstructor
public class LogIngestService {

    private static final Logger log = LoggerFactory.getLogger(LogIngestService.class);

    private final LogEventRepository logEventRepository;
    private final LogPipelineService logPipelineService;

    @Transactional
    public int ingest(Application application, LogIngestRequest request) {
        Instant receivedAt = Instant.now();
        List<LogEvent> events = new ArrayList<>(request.getEvents().size());
        List<LogPipelineEngine.PipelineResult> processedEvents =
                logPipelineService.applyForIngestBatch(application, request.getEvents());
        int pipelineDropped = 0;

        for (int index = 0; index < request.getEvents().size(); index++) {
            LogEventPayload payload = request.getEvents().get(index);
            LogPipelineEngine.PipelineResult processed = processedEvents.get(index);
            if (processed.dropped()) {
                pipelineDropped++;
                continue;
            }
            events.add(LogEvent.builder()
                    .application(application)
                    .level(payload.getLevel())
                    .message(processed.message())
                    .context(processed.context())
                    .timestamp(payload.getTimestamp())
                    .receivedAt(receivedAt)
                    .build());
        }

        if (!events.isEmpty()) {
            logEventRepository.saveAll(events);
        }

        // Logs the batch size only, never the event contents.
        log.info("Accepted {} log events for application id={} (pipeline dropped {})",
                events.size(), application.getId(), pipelineDropped);

        return events.size();
    }
}
