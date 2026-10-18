package com.monitoring.collector.entity;

import com.monitoring.apps.entity.Application;
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

/**
 * A downsampled metric point produced by the background aggregation job.
 *
 * <p>Raw samples older than the downsampling window are grouped by application, metric name,
 * tag set and time bucket; {@code valueAvg}/{@code valueMax}/{@code valueMin} are the
 * aggregates of that bucket and {@code sampleCount} is how many raw samples it covers.
 *
 * <p>{@code bucketEpoch} is the bucket start in epoch seconds (UTC), not a timestamp, so it
 * matches {@code FLOOR(UNIX_TIMESTAMP(event_time) / bucketSeconds) * bucketSeconds} without
 * depending on the database session time zone.
 *
 * <p>{@code tagSignature} is a deterministic, truncated encoding of the tag set
 * (see {@link #MAX_TAG_SIGNATURE_LENGTH}); it identifies the series together with the
 * application, name, granularity and bucket.
 */
@Entity
@Table(
        name = "metric_aggregates",
        indexes = @Index(
                name = "idx_metric_aggregates_bucket_epoch",
                columnList = "bucket_epoch"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricAggregate {

    /** Must match the {@code tag_signature} column width. */
    public static final int MAX_TAG_SIGNATURE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false, length = 200)
    private String name;

    // Stored as VARCHAR for portability (Hibernate would otherwise expect a native ENUM).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private MetricGranularity granularity;

    @Column(name = "bucket_epoch", nullable = false)
    private long bucketEpoch;

    @Column(name = "tag_signature", nullable = false, length = MAX_TAG_SIGNATURE_LENGTH)
    @Builder.Default
    private String tagSignature = "";

    @Column(name = "sample_count", nullable = false)
    private long sampleCount;

    @Column(name = "value_avg", nullable = false)
    private double valueAvg;

    @Column(name = "value_max", nullable = false)
    private double valueMax;

    @Column(name = "value_min", nullable = false)
    private double valueMin;
}
