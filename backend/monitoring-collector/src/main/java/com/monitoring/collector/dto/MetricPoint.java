package com.monitoring.collector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

/**
 * Одна точка метрики в батче приёма.
 * {@code timestamp} — момент измерения в UTC.
 * {@code tags} — опциональные метки, например {"host": "server-1", "env": "prod"}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricPoint {

    @NotBlank(message = "metric name is required")
    @Size(max = 200, message = "metric name must be at most 200 characters")
    private String name;

    @NotNull(message = "metric value is required")
    private Double value;

    @NotNull(message = "metric timestamp is required")
    private Instant timestamp;

    @Size(max = 50, message = "at most 50 tags are allowed per metric")
    private Map<String, String> tags;
}
