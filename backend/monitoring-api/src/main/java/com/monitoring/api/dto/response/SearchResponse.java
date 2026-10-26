package com.monitoring.api.dto.response;

import java.util.List;

/** Grouped-by-type resource search results. */
public record SearchResponse(List<SearchResultResponse> results) {
}
