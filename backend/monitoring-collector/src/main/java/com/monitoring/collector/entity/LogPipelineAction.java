package com.monitoring.collector.entity;

/** The transformation applied by a log pipeline rule when its filter matches. */
public enum LogPipelineAction {
    DROP,
    PARSE_REGEX,
    ADD_ATTRIBUTE,
    MASK
}
