package com.monitoring.collector.repository;

import com.monitoring.collector.entity.LogEvent;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Persistence for log events. Filtering is expressed with {@link JpaSpecificationExecutor}
 * so optional criteria (level, text search) can be added conditionally without null
 * parameters in JPQL, and so paging/sorting stays server-side.
 */
public interface LogEventRepository extends JpaRepository<LogEvent, Long>, JpaSpecificationExecutor<LogEvent> {

    /**
     * Number of log events with an event time in {@code [from, to)} for the given applications.
     * Scoped by application ids (tenant ownership); an empty id collection must be handled by
     * the caller (an empty {@code IN} clause is not valid SQL).
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "5000"))
    @Query("SELECT COUNT(l) FROM LogEvent l"
            + " WHERE l.application.id IN :applicationIds"
            + " AND l.timestamp >= :from AND l.timestamp < :to")
    long countByApplicationsAndTimeRange(@Param("applicationIds") Collection<Long> applicationIds,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);

    /**
     * Error count for a tenant's applications in a half-open time range.
     *
     * <p>The query hint caps execution time because scheduled reports aggregate over a potentially
     * large historical window on behalf of a tenant.
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "5000"))
    @Query("SELECT COUNT(l) FROM LogEvent l"
            + " WHERE l.application.id IN :applicationIds"
            + " AND l.level = com.monitoring.collector.entity.LogLevel.ERROR"
            + " AND l.timestamp >= :from AND l.timestamp < :to")
    long countErrorsByApplicationsAndTimeRange(@Param("applicationIds") Collection<Long> applicationIds,
                                               @Param("from") Instant from,
                                               @Param("to") Instant to);

    /**
     * Most frequent error messages, capped to 200 characters for grouping. The message prefix is
     * untrusted text and must still be rendered as plain text by consumers.
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "5000"))
    @Query("SELECT SUBSTRING(l.message, 1, 200), COUNT(l) FROM LogEvent l"
            + " WHERE l.application.id IN :applicationIds"
            + " AND l.level = com.monitoring.collector.entity.LogLevel.ERROR"
            + " AND l.timestamp >= :from AND l.timestamp < :to"
            + " GROUP BY SUBSTRING(l.message, 1, 200)"
            + " ORDER BY COUNT(l) DESC, SUBSTRING(l.message, 1, 200) ASC")
    List<Object[]> findTopErrorMessages(@Param("applicationIds") Collection<Long> applicationIds,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to,
                                        Pageable pageable);

    /**
     * Counts log events for one application inside a half-open time range, optionally filtered
     * by severity and a literal (already escaped) case-insensitive LIKE pattern.
     *
     * <p>This is a native {@code COUNT(*)} so log-based alert evaluation never materializes log
     * rows. Both optional filters use a non-null sentinel (empty string) instead of a null
     * parameter: native queries with untyped null parameters are not portable between MySQL and
     * the H2 test database. The search pattern is supplied by the service with {@code %},
     * {@code _} and {@code !} escaped using {@code !} as the escape character, so log text is
     * matched literally. A literal backslash is avoided because MySQL and H2 interpret it
     * differently inside SQL string literals.
     *
     * <p>The query hint caps execution time. The caller treats a timeout as "no observation" and
     * skips the evaluation round rather than risking an unbounded scan.
     */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "5000"))
    @Query(value = "SELECT COUNT(*) FROM log_events"
            + " WHERE application_id = :applicationId"
            + " AND event_time >= :from AND event_time < :to"
            + " AND (:level = '' OR level = :level)"
            + " AND (:searchPattern = '' OR LOWER(message) LIKE :searchPattern ESCAPE '!')",
            nativeQuery = true)
    long countForAlert(@Param("applicationId") Long applicationId,
                       @Param("from") Instant from,
                       @Param("to") Instant to,
                       @Param("level") String level,
                       @Param("searchPattern") String searchPattern);

    /**
     * Number of log events recorded for the given applications since {@code since}.
     *
     * <p>Scoped by application ids so a tenant can only count its own events; an empty id
     * collection must be handled by the caller (an empty {@code IN} clause is not valid SQL).
     */
    @Query("SELECT COUNT(l) FROM LogEvent l"
            + " WHERE l.application.id IN :applicationIds AND l.timestamp >= :since")
    long countSince(@Param("applicationIds") Collection<Long> applicationIds,
                    @Param("since") Instant since);

    /**
     * Ids of log events older than {@code cutoff} for one application, bounded by the
     * {@link org.springframework.data.domain.Pageable}.
     *
     * <p>Used by the per-organization retention purge to delete expired rows in bounded
     * batches. Scoping by a single application id (obtained from the tenant's applications)
     * keeps the deletion inside the organization.
     */
    @Query("SELECT l.id FROM LogEvent l"
            + " WHERE l.application.id = :applicationId AND l.timestamp < :cutoff"
            + " ORDER BY l.id")
    List<Long> findIdsByApplicationBefore(@Param("applicationId") Long applicationId,
                                          @Param("cutoff") Instant cutoff,
                                          Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM LogEvent l WHERE l.id IN :ids")
    int deleteByIds(@Param("ids") Collection<Long> ids);
}
