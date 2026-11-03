package com.monitoring.collector.dto.request;

import com.monitoring.collector.dto.request.LogEventPayload;
import com.monitoring.collector.entity.LogLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sample event used to simulate the persisted pipeline rules for one application. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestLogPipelineRequest {

    @NotNull(message = "applicationId is required")
    @Positive(message = "applicationId must be positive")
    private Long applicationId;

    @NotNull(message = "level is required")
    private LogLevel level;

    @NotBlank(message = "message must not be blank")
    @Size(max = LogEventPayload.MAX_MESSAGE_LENGTH,
            message = "message must be at most " + LogEventPayload.MAX_MESSAGE_LENGTH + " characters")
    private String message;

    @Size(max = LogEventPayload.MAX_CONTEXT_LENGTH,
            message = "context must be at most " + LogEventPayload.MAX_CONTEXT_LENGTH + " characters")
    private String context;
}
