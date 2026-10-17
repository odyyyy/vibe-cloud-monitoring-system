package com.monitoring.collector.dto;

import com.monitoring.collector.entity.LogLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single log event inside an ingestion batch.
 *
 * <p>Sizes are bounded at the contract level: {@code message} and {@code context} are
 * untrusted and could otherwise be used to exhaust memory or storage.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEventPayload {

    public static final int MAX_MESSAGE_LENGTH = 8192;

    // MySQL TEXT stores at most 65535 bytes; 8192 characters fit even when every character
    // is 4 bytes in UTF-8, so a validated payload can never overflow the column.
    public static final int MAX_CONTEXT_LENGTH = 8192;

    @NotNull(message = "level is required")
    private LogLevel level;

    @NotBlank(message = "message must not be blank")
    @Size(max = MAX_MESSAGE_LENGTH, message = "message must be at most " + MAX_MESSAGE_LENGTH + " characters")
    private String message;

    @Size(max = MAX_CONTEXT_LENGTH, message = "context must be at most " + MAX_CONTEXT_LENGTH + " characters")
    private String context;

    @NotNull(message = "timestamp is required")
    private Instant timestamp;
}
