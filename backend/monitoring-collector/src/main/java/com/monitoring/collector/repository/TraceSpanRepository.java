package com.monitoring.collector.repository;

import com.monitoring.collector.entity.TraceSpan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persistence for trace spans.
 *
 * <p>The detail lookup returns every span of a trace ordered by start time (id as a
 * deterministic tie-breaker). The recent-traces listing is expressed as a grouped native
 * query in {@code TraceQueryService} because it aggregates whole traces.
 */
public interface TraceSpanRepository extends JpaRepository<TraceSpan, Long> {

    List<TraceSpan> findByTraceIdOrderByStartTimeAscIdAsc(String traceId);
}
