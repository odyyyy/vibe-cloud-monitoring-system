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
 * A single structured log event, scoped to an application (tenant boundary).
 *
 * <p>{@code message} and {@code context} are untrusted input: they may contain arbitrary
 * text, secrets or hostile markup. They are stored and returned verbatim; rendering is the
 * responsibility of the client, which must never treat them as HTML.
 *
 * <p>{@code timestamp} is the event time (UTC) supplied by the sender, {@code receivedAt}
 * is the server-side ingest time (UTC).
 */
@Entity
@Table(
        name = "log_events",
        indexes = @Index(
                name = "idx_log_events_app_event_time",
                columnList = "application_id,event_time"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    // Store enum names as VARCHAR to stay portable across databases (Hibernate on MySQL
    // would otherwise expect a native ENUM column).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private LogLevel level;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Optional structured context as a JSON string; not interpreted by the backend. */
    @Column(name = "context", columnDefinition = "TEXT")
    private String context;

    @Column(name = "event_time", nullable = false)
    private Instant timestamp;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
