package com.monitoring.collector.repository;

import com.monitoring.collector.entity.LogEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Persistence for log events. Filtering is expressed with {@link JpaSpecificationExecutor}
 * so optional criteria (level, text search) can be added conditionally without null
 * parameters in JPQL, and so paging/sorting stays server-side.
 */
public interface LogEventRepository extends JpaRepository<LogEvent, Long>, JpaSpecificationExecutor<LogEvent> {

    long countByApplication_Id(Long applicationId);
}
