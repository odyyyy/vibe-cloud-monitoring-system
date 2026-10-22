package com.monitoring.revisions.exception;

/** A stored snapshot cannot be parsed or does not satisfy the current schema. */
public class InvalidConfigSnapshotException extends RuntimeException {

    public InvalidConfigSnapshotException(String message) {
        super(message);
    }

    public InvalidConfigSnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
