package com.monitoring.revisions.dto;

import com.monitoring.revisions.entity.ConfigChangeType;
import com.monitoring.revisions.entity.ConfigRevision;

import java.time.Instant;

/** Revision metadata for the history timeline; deliberately excludes the snapshot. */
public record ConfigRevisionSummaryResponse(
        int revisionNumber,
        String userDisplayName,
        ConfigChangeType changeType,
        Instant createdAt,
        Integer restoredFromRevisionNumber
) {

    public static ConfigRevisionSummaryResponse from(ConfigRevision revision, String userDisplayName) {
        return new ConfigRevisionSummaryResponse(
                revision.getRevisionNumber(),
                userDisplayName,
                revision.getChangeType(),
                revision.getCreatedAt(),
                revision.getRestoredFromRevisionNumber());
    }
}
