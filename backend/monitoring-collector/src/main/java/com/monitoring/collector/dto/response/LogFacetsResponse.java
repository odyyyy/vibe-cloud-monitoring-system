package com.monitoring.collector.dto.response;

import java.util.List;
import java.util.Map;

/**
 * The Top-N values of the requested log fields for the current search context.
 *
 * @param facets     the values per requested field, ordered by descending count
 * @param approximate {@code true} when the counts were computed over a bounded subset of the
 *                   matching rows because the scan limit was reached
 * @param scanLimit  the maximum number of rows the aggregation scanned; reported so the client
 *                   can tell the user how the numbers were obtained
 */
public record LogFacetsResponse(
        Map<String, List<LogFacetValueResponse>> facets,
        boolean approximate,
        int scanLimit) {
}
