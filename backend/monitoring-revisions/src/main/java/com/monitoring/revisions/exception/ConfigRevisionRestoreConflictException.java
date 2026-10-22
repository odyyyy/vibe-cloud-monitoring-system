package com.monitoring.revisions.exception;

/**
 * A restore cannot be applied because the snapshot references resources that no longer exist
 * or no longer belong to the organization. Nothing is changed.
 */
public class ConfigRevisionRestoreConflictException extends RuntimeException {

    public ConfigRevisionRestoreConflictException(String message) {
        super(message);
    }
}
