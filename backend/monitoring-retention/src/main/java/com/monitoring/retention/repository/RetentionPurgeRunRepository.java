package com.monitoring.retention.repository;

import com.monitoring.retention.entity.RetentionPurgeRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RetentionPurgeRunRepository extends JpaRepository<RetentionPurgeRun, Long> {

    /** Most recent runs of one organization, newest first (id is the deterministic tie-breaker). */
    @Query("SELECT r FROM RetentionPurgeRun r"
            + " WHERE r.organizationId = :organizationId"
            + " ORDER BY r.startedAt DESC, r.id DESC")
    List<RetentionPurgeRun> findRecent(@Param("organizationId") Long organizationId, Pageable pageable);
}
