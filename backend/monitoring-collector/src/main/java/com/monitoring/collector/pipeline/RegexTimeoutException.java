package com.monitoring.collector.pipeline;

/** Raised internally when a regex evaluation exceeds its deadline. */
final class RegexTimeoutException extends RuntimeException {

    RegexTimeoutException() {
        super("Regular expression evaluation timed out");
    }
}
