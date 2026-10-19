package com.monitoring.collector.repository;

import com.monitoring.collector.entity.TraceSpan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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

    /**
     * Number of trace spans with a start time in {@code [from, to)} for the given applications.
     * Scoped by application ids (tenant ownership); an empty id collection must be handled by
     * the caller (an empty {@code IN} clause is not valid SQL).
     */
    @Query("SELECT COUNT(t) FROM TraceSpan t"
            + " WHERE t.application.id IN :applicationIds"
            + " AND t.startTime >= :from AND t.startTime < :to")
    long countByApplicationsAndTimeRange(@Param("applicationIds") Collection<Long> applicationIds,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);
}
