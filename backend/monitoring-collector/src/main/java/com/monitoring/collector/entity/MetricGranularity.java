package com.monitoring.collector.entity;

/**
 * Resolution of a stored metric sample.
 *
 * <p>Values are part of the storage contract and are stored as VARCHAR so the schema stays
 * portable across databases.
 */
public enum MetricGranularity {
    RAW,
    HOURLY,
    DAILY
}
