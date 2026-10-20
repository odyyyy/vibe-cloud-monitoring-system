package com.monitoring.collector.exception;

public class InvalidExportFormatException extends RuntimeException {

    public InvalidExportFormatException() {
        super("Unsupported export format. Use 'csv' or 'json'.");
    }
}
