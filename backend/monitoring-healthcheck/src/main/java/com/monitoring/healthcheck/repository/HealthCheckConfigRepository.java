package com.monitoring.healthcheck.repository;

import com.monitoring.healthcheck.entity.HealthCheckConfig;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HealthCheckConfigRepository extends JpaRepository<HealthCheckConfig, Long> {

    List<HealthCheckConfig> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    Optional<HealthCheckConfig> findByIdAndOrganizationId(Long id, Long organizationId);

    /** Active configurations considered by the scheduler. */
    List<HealthCheckConfig> findByActiveTrue();

    /** Bounded, tenant-scoped name search used by the global command palette. */
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
    @Query("""
            SELECT h FROM HealthCheckConfig h
            WHERE h.organizationId = :organizationId
              AND LOCATE(:needle, LOWER(h.name)) > 0
            ORDER BY CASE WHEN LOCATE(:needle, LOWER(h.name)) = 1 THEN 0 ELSE 1 END,
                     LOWER(h.name), h.id
            """)
    List<HealthCheckConfig> searchByName(@Param("organizationId") Long organizationId,
                                         @Param("needle") String needle,
                                         Pageable pageable);
}
