package com.monitoring.revisions.entity;

/**
 * How a revision came to exist.
 *
 * <p>{@link #BASELINE} captures the state that existed before versioning was introduced; its
 * author is unknown, so {@code userId} is null. {@link #RESTORE} records a rollback and points
 * at the revision it was restored from.
 */
public enum ConfigChangeType {
    BASELINE,
    CREATE,
    UPDATE,
    RESTORE
}
