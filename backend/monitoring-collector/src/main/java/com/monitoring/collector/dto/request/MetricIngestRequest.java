package com.monitoring.collector.dto.request;


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
 * Request body for metric ingestion.
 * The batch upper bound is fixed at the contract level: 1000 points per request.
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
