package com.monitoring.collector.entity;

import com.monitoring.apps.entity.Application;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * A stored metric point scoped to an application (tenant boundary).
 * {@code timestamp} is the measurement instant (event time, UTC).
 * {@code receivedAt} is the server ingest instant (ingest time, UTC).
 */
@Entity
@Table(
        name = "metric_samples",
        indexes = @Index(
                name = "idx_metric_samples_app_name_time",
                columnList = "application_id,name,event_time"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "metric_value", nullable = false)
    private double value;

    @Column(name = "event_time", nullable = false)
    private Instant timestamp;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "metric_sample_tags",
            joinColumns = @JoinColumn(name = "metric_sample_id")
    )
    @MapKeyColumn(name = "tag_key", length = 100)
    @Column(name = "tag_value", length = 500)
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();
}
