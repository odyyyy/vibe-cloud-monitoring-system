package com.monitoring.revisions.exception;

/** The requested revision does not exist for the tenant-scoped resource. */
public class ConfigRevisionNotFoundException extends RuntimeException {

    public ConfigRevisionNotFoundException(int revisionNumber) {
        super("Revision " + revisionNumber + " was not found");
    }
}
