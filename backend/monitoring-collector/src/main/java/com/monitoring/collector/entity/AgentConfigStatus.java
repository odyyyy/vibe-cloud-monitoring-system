package com.monitoring.collector.entity;

/** Sync state reported by an agent for the active configuration version. */
public enum AgentConfigStatus {
    UNKNOWN,
    PENDING,
    SYNCED,
    ERROR
}
