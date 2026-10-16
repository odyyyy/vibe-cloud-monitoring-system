package com.monitoring.collector.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Тело запроса на приём метрик.
 * Верхняя граница батча зафиксирована на уровне контракта — 1000 точек за запрос.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricIngestRequest {

    public static final int MAX_BATCH_SIZE = 1000;

    @NotEmpty(message = "metrics must not be empty")
    @Size(max = MAX_BATCH_SIZE, message = "at most " + MAX_BATCH_SIZE + " metrics are allowed per request")
    @Valid
    private List<MetricPoint> metrics;
}
