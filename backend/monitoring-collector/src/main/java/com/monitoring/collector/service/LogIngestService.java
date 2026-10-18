package com.monitoring.collector.service;

import com.monitoring.collector.dto.request.LogEventPayload;
import com.monitoring.collector.dto.request.LogIngestRequest;
import com.monitoring.collector.entity.LogEvent;
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

    @Transactional
    public int ingest(Application application, LogIngestRequest request) {
        Instant receivedAt = Instant.now();
        List<LogEvent> events = new ArrayList<>(request.getEvents().size());

        for (LogEventPayload payload : request.getEvents()) {
            events.add(LogEvent.builder()
                    .application(application)
                    .level(payload.getLevel())
                    .message(payload.getMessage())
                    .context(payload.getContext())
                    .timestamp(payload.getTimestamp())
                    .receivedAt(receivedAt)
                    .build());
        }

        logEventRepository.saveAll(events);

        // Logs the batch size only, never the event contents.
        log.info("Accepted {} log events for application id={}", events.size(), application.getId());

        return events.size();
    }
}
