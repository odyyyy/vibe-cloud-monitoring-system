package com.monitoring.collector.dto;

import com.monitoring.collector.entity.SpanStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single span inside an ingestion batch.
 *
 * <p>The owning application is never taken from the payload: it is derived from the
 * {@code X-API-Key}, so a caller cannot write into another tenant's data. Sizes are bounded
 * at the contract level because every field is untrusted input.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpanPayload {

    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_SERVICE_NAME_LENGTH = 128;
    public static final int MAX_OPERATION_NAME_LENGTH = 256;

    // MySQL TEXT stores at most 65535 bytes; 8192 characters fit even when every character
    // is 4 bytes in UTF-8, so a validated payload can never overflow the column.
    public static final int MAX_ATTRIBUTES_LENGTH = 8192;

    @NotBlank(message = "traceId must not be blank")
    @Size(max = MAX_ID_LENGTH, message = "traceId must be at most " + MAX_ID_LENGTH + " characters")
    private String traceId;

    @NotBlank(message = "spanId must not be blank")
    @Size(max = MAX_ID_LENGTH, message = "spanId must be at most " + MAX_ID_LENGTH + " characters")
    private String spanId;

    @Size(max = MAX_ID_LENGTH, message = "parentSpanId must be at most " + MAX_ID_LENGTH + " characters")
    private String parentSpanId;

    @NotBlank(message = "serviceName must not be blank")
    @Size(max = MAX_SERVICE_NAME_LENGTH, message = "serviceName must be at most "
            + MAX_SERVICE_NAME_LENGTH + " characters")
    private String serviceName;

    @NotBlank(message = "operationName must not be blank")
    @Size(max = MAX_OPERATION_NAME_LENGTH, message = "operationName must be at most "
            + MAX_OPERATION_NAME_LENGTH + " characters")
    private String operationName;

    @NotNull(message = "startTime is required")
    private Instant startTime;

    @NotNull(message = "durationMs is required")
    @PositiveOrZero(message = "durationMs must be greater than or equal to 0")
    private Long durationMs;

    @NotNull(message = "status is required")
    private SpanStatus status;

    @Size(max = MAX_ATTRIBUTES_LENGTH, message = "attributes must be at most "
            + MAX_ATTRIBUTES_LENGTH + " characters")
    private String attributes;
}
