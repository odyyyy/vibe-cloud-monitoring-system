package com.monitoring.collector.repository;

import com.monitoring.collector.entity.LogEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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
     * Number of log events recorded for the given applications since {@code since}.
     *
     * <p>Scoped by application ids so a tenant can only count its own events; an empty id
     * collection must be handled by the caller (an empty {@code IN} clause is not valid SQL).
     */
    @Query("SELECT COUNT(l) FROM LogEvent l"
            + " WHERE l.application.id IN :applicationIds AND l.timestamp >= :since")
    long countSince(@Param("applicationIds") Collection<Long> applicationIds,
                    @Param("since") Instant since);

    /** Ids of log events older than {@code cutoffEpoch} (epoch seconds), bounded by {@code limit}. */
    @Query(value = "SELECT id FROM log_events"
            + " WHERE UNIX_TIMESTAMP(event_time) < :cutoffEpoch"
            + " ORDER BY id"
            + " LIMIT :limit", nativeQuery = true)
    List<Long> findIdsBefore(@Param("cutoffEpoch") long cutoffEpoch, @Param("limit") int limit);

    @Modifying
    @Transactional
    @Query("DELETE FROM LogEvent l WHERE l.id IN :ids")
    int deleteByIds(@Param("ids") Collection<Long> ids);
}
