package com.monitoring.collector.dto.response;

import com.monitoring.collector.entity.LogEvent;
import com.monitoring.collector.entity.LogLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Public representation of a log event. It never exposes the persistence entity directly.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEventResponse {

    private Long id;
    private Long applicationId;
    private Instant timestamp;
    private LogLevel level;
    private String message;
    private String context;

    public static LogEventResponse fromEntity(LogEvent event) {
        return LogEventResponse.builder()
                .id(event.getId())
                .applicationId(event.getApplication().getId())
                .timestamp(event.getTimestamp())
                .level(event.getLevel())
                .message(event.getMessage())
                .context(event.getContext())
                .build();
    }
}
