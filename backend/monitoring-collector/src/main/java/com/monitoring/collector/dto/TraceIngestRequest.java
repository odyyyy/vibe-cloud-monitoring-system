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
 * Body of a trace ingestion request. The batch size and the per-trace span count are part of
 * the contract: an oversized batch is rejected up front so a single request cannot exhaust
 * memory while it is being processed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TraceIngestRequest {

    /** Maximum number of spans in a single request. */
    public static final int MAX_BATCH_SIZE = 1000;

    /**
     * Maximum number of spans accepted for a single trace inside one request. Traces with a
     * huge number of spans are pathological and are rejected instead of being materialized.
     */
    public static final int MAX_SPANS_PER_TRACE = 1000;

    @NotEmpty(message = "spans must not be empty")
    @Size(max = MAX_BATCH_SIZE, message = "at most " + MAX_BATCH_SIZE + " spans are allowed per request")
    @Valid
    private List<SpanPayload> spans;
}
