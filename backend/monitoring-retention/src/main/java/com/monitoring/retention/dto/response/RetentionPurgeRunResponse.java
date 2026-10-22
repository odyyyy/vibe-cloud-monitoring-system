package com.monitoring.retention.dto.response;

import com.monitoring.retention.entity.PurgeRunStatus;
import com.monitoring.retention.entity.RetentionDataType;
import com.monitoring.retention.entity.RetentionPurgeRun;

import java.time.Instant;

/** Read-only view of a single purge run for the organization settings page. */
public record RetentionPurgeRunResponse(
        Long id,
        RetentionDataType dataType,
        Instant startedAt,
        Instant finishedAt,
        long rowsDeleted,
        PurgeRunStatus status,
        String errorMessage
) {

    public static RetentionPurgeRunResponse from(RetentionPurgeRun run) {
        return new RetentionPurgeRunResponse(
                run.getId(),
                run.getDataType(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getRowsDeleted(),
                run.getStatus(),
                run.getErrorMessage());
    }
}
