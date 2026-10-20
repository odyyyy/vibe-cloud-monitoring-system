package com.monitoring.collector.export;

import java.util.List;

/**
 * Bounded rows selected for a synchronous export.
 *
 * <p>{@code totalRows} is the number of rows that matched the filters before the export
 * safety limit was applied. When it is greater than the number of returned rows the export
 * was truncated and callers can tell the user to narrow the query.
 */
public record ExportData<T>(List<T> rows, long totalRows) {

    public boolean isTruncated() {
        return totalRows > rows.size();
    }
}
