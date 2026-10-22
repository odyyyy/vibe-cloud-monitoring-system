package com.monitoring.retention.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * History of purge attempts, one row per organization, data type and scheduled run.
 *
 * <p>Rows contain only counts and a short error message. Telemetry contents are never copied
 * here, and a failed attempt does not prevent the next run from retrying the same range.
 */
@Entity
@Table(
        name = "retention_purge_runs",
        indexes = @Index(
                name = "idx_retention_purge_runs_org_started",
                columnList = "organization_id,started_at"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetentionPurgeRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "data_type", nullable = false, length = 16)
    private RetentionDataType dataType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "rows_deleted", nullable = false)
    private long rowsDeleted;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private PurgeRunStatus status;

    /** Short, non-sensitive failure description; never contains telemetry contents. */
    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
