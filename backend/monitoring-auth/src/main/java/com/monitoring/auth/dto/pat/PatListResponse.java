package com.monitoring.auth.dto.pat;

import java.util.List;

public record PatListResponse(
        List<PatSummary> summaries
) {
}