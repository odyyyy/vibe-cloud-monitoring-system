package com.monitoring.collector.service;

import com.monitoring.collector.dto.request.SpanPayload;
import com.monitoring.collector.dto.request.TraceIngestRequest;
import com.monitoring.collector.entity.TraceSpan;
import com.monitoring.collector.exception.InvalidTraceException;
import com.monitoring.collector.repository.TraceSpanRepository;
import com.monitoring.apps.entity.Application;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accepts span batches from agents and persists them scoped to the owning application.
 *
 * <p>The payload is validated at the API boundary ({@code @Valid}): an empty batch, more than
 * {@link TraceIngestRequest#MAX_BATCH_SIZE} spans or oversized fields are rejected with HTTP
 * 400, and a single trace may not carry more than
 * {@link TraceIngestRequest#MAX_SPANS_PER_TRACE} spans in one request. The application is
 * derived from the {@code X-API-Key}, never from the payload.
 *
 * <p>Span attributes are never logged: they are untrusted and may contain secrets.
 */
@Service
@RequiredArgsConstructor
public class TraceIngestService {

    private static final Logger log = LoggerFactory.getLogger(TraceIngestService.class);

    private final TraceSpanRepository traceSpanRepository;

    @Transactional
    public int ingest(Application application, TraceIngestRequest request) {
        enforcePerTraceLimit(request.getSpans());

        List<TraceSpan> spans = new ArrayList<>(request.getSpans().size());
        for (SpanPayload payload : request.getSpans()) {
            spans.add(toEntity(application, payload));
        }

        traceSpanRepository.saveAll(spans);

        // Logs the batch size only, never the span contents.
        log.info("Accepted {} trace spans for application id={}", spans.size(), application.getId());

        return spans.size();
    }

    private void enforcePerTraceLimit(List<SpanPayload> payloads) {
        Map<String, Integer> perTrace = new HashMap<>();
        for (SpanPayload payload : payloads) {
            int count = perTrace.merge(payload.getTraceId(), 1, Integer::sum);
            if (count > TraceIngestRequest.MAX_SPANS_PER_TRACE) {
                throw new InvalidTraceException("A trace must not carry more than "
                        + TraceIngestRequest.MAX_SPANS_PER_TRACE + " spans per request");
            }
        }
    }

    private TraceSpan toEntity(Application application, SpanPayload payload) {
        return TraceSpan.builder()
                .traceId(payload.getTraceId())
                .spanId(payload.getSpanId())
                .parentSpanId(payload.getParentSpanId())
                .application(application)
                .serviceName(payload.getServiceName())
                .operationName(payload.getOperationName())
                .startTime(payload.getStartTime())
                .endTime(payload.getStartTime().plusMillis(payload.getDurationMs()))
                .durationMs(payload.getDurationMs())
                .status(payload.getStatus())
                .attributes(payload.getAttributes())
                .build();
    }
}
