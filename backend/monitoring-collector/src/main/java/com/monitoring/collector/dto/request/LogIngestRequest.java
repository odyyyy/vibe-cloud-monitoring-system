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
 * Body of a log ingestion request. The batch size is part of the contract and is capped
 * at {@link #MAX_BATCH_SIZE} events per request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogIngestRequest {

    public static final int MAX_BATCH_SIZE = 1000;

    @NotEmpty(message = "events must not be empty")
    @Size(max = MAX_BATCH_SIZE, message = "at most " + MAX_BATCH_SIZE + " events are allowed per request")
    @Valid
    private List<LogEventPayload> events;
}
