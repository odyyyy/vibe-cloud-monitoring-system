package com.monitoring.collector.dto;

import com.monitoring.collector.entity.SpanStatus;
import com.monitoring.collector.entity.TraceSpan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Public representation of a single span. It never exposes the persistence entity directly.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TraceSpanResponse {

    private Long id;
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String serviceName;
    private String operationName;
    private Instant startTime;
    private long durationMs;
    private SpanStatus status;
    private String attributes;

    public static TraceSpanResponse fromEntity(TraceSpan span) {
        return TraceSpanResponse.builder()
                .id(span.getId())
                .traceId(span.getTraceId())
                .spanId(span.getSpanId())
                .parentSpanId(span.getParentSpanId())
                .serviceName(span.getServiceName())
                .operationName(span.getOperationName())
                .startTime(span.getStartTime())
                .durationMs(span.getDurationMs())
                .status(span.getStatus())
                .attributes(span.getAttributes())
                .build();
    }
}
