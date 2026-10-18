package com.monitoring.collector.entity;

import com.monitoring.storage.entity.Application;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A single span of a distributed trace, scoped to an application (tenant boundary).
 *
 * <p>Spans of one trace are linked two ways: by {@code traceId} (all spans of a request)
 * and by {@code spanId}/{@code parentSpanId} (the call hierarchy shown by the waterfall).
 *
 * <p>{@code attributes} is untrusted input stored verbatim as a JSON string; the backend
 * never interprets it. {@code startTime} is the span start (UTC) supplied by the sender and
 * {@code endTime} is derived at ingest as {@code startTime + durationMs}.
 */
@Entity
@Table(
        name = "trace_spans",
        indexes = {
                @Index(name = "idx_trace_spans_trace", columnList = "trace_id"),
                @Index(name = "idx_trace_spans_app_start", columnList = "application_id,start_time"),
                @Index(name = "idx_trace_spans_app_service", columnList = "application_id,service_name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TraceSpan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "span_id", nullable = false, length = 64)
    private String spanId;

    /** Parent span id, or {@code null} for the root span. */
    @Column(name = "parent_span_id", length = 64)
    private String parentSpanId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "service_name", nullable = false, length = 128)
    private String serviceName;

    @Column(name = "operation_name", nullable = false, length = 256)
    private String operationName;

    @Column(name = "start_time", nullable = false, columnDefinition = "TIMESTAMP(3)")
    private Instant startTime;

    /** Derived end time ({@code startTime + durationMs}) kept for portable trace aggregation. */
    @Column(name = "end_time", nullable = false, columnDefinition = "TIMESTAMP(3)")
    private Instant endTime;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    // Store enum names as VARCHAR to stay portable across databases (Hibernate on MySQL
    // would otherwise expect a native ENUM column).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private SpanStatus status;

    /** Optional structured attributes as a JSON string; not interpreted by the backend. */
    @Column(name = "attributes", columnDefinition = "TEXT")
    private String attributes;
}
