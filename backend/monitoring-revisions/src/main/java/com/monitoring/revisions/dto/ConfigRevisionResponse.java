package com.monitoring.revisions.dto;

import com.monitoring.revisions.entity.ConfigChangeType;
import com.monitoring.revisions.entity.ConfigRevision;

import java.time.Instant;

/** A single revision including the stored snapshot document. */
public record ConfigRevisionResponse(
        int revisionNumber,
        String userDisplayName,
        ConfigChangeType changeType,
        Instant createdAt,
        Integer restoredFromRevisionNumber,
        int schemaVersion,
        String snapshot
) {

    public static ConfigRevisionResponse from(ConfigRevision revision, String userDisplayName) {
        return new ConfigRevisionResponse(
                revision.getRevisionNumber(),
                userDisplayName,
                revision.getChangeType(),
                revision.getCreatedAt(),
                revision.getRestoredFromRevisionNumber(),
                revision.getSchemaVersion(),
                revision.getSnapshot());
    }
}
